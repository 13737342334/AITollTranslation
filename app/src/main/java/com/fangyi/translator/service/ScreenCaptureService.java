package com.fangyi.translator.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import com.fangyi.translator.App;
import com.fangyi.translator.R;

import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCapture";

    public static final String ACTION_START_CAPTURE = "START_CAPTURE";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread handlerThread;
    private Handler backgroundHandler;

    private int displayWidth, displayHeight, displayDpi;

    private static ScreenshotCallback screenshotCallback;

    public interface ScreenshotCallback {
        void onScreenshot(Bitmap bitmap);
        void onError(String error);
    }

    public static void setScreenshotCallback(ScreenshotCallback callback) {
        screenshotCallback = callback;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("ScreenCaptureThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        displayWidth = metrics.widthPixels;
        displayHeight = metrics.heightPixels;
        displayDpi = metrics.densityDpi;

        startForeground(2001, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START_CAPTURE.equals(intent.getAction())) {
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");

        if (data != null && resultCode != -1) {
            setupMediaProjection(resultCode, data);
        }

        return START_NOT_STICKY;
    }

    private void setupMediaProjection(int resultCode, Intent data) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mpm == null) {
            notifyError("无法获取MediaProjection服务");
            return;
        }

        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            notifyError("MediaProjection创建失败");
            return;
        }

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, backgroundHandler);

        backgroundHandler.postDelayed(this::captureScreen, 300);
    }

    private void captureScreen() {
        if (mediaProjection == null) {
            notifyError("MediaProjection未就绪");
            return;
        }

        try {
            imageReader = ImageReader.newInstance(displayWidth, displayHeight,
                    PixelFormat.RGBA_8888, 2);

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        Bitmap bitmap = imageToBitmap(image);
                        if (screenshotCallback != null) {
                            screenshotCallback.onScreenshot(bitmap);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "capture error", e);
                    notifyError("截图处理失败");
                } finally {
                    if (image != null) image.close();
                    stopSelf();
                }
            }, backgroundHandler);

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    displayWidth, displayHeight, displayDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null, backgroundHandler
            );

        } catch (Exception e) {
            Log.e(TAG, "createVirtualDisplay error", e);
            notifyError("截图失败: " + e.getMessage());
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();

        Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth() + rowPadding / pixelStride,
                image.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private void notifyError(String msg) {
        if (screenshotCallback != null) {
            screenshotCallback.onError(msg);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, App.CHANNEL_TRANSLATION)
                .setContentTitle("页面翻译")
                .setContentText("正在截取屏幕进行翻译...")
                .setSmallIcon(R.drawable.ic_camera)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
