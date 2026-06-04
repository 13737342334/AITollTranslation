package com.fangyi.translator.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;

import com.fangyi.translator.R;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import com.fangyi.translator.App;

import java.nio.ByteBuffer;

/**
 * Service that holds a MediaProjection for screen capture.
 * Takes screenshots on demand and passes the bitmap to a callback.
 */
public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCapture";

    private static final int VIRTUAL_DISPLAY_WIDTH = 1080;
    private static final int VIRTUAL_DISPLAY_HEIGHT = 2340;
    private static final int VIRTUAL_DISPLAY_DPI = 280;

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

    public static void takeScreenshot(Context context) {
        Intent intent = new Intent(context, ScreenCaptureService.class);
        intent.setAction("TAKE_SCREENSHOT");
        context.startForegroundService(intent);
    }

    public static Intent createStartIntent(Context context, int resultCode, Intent data) {
        Intent intent = new Intent(context, ScreenCaptureService.class);
        intent.setAction("START_MEDIAPROJECTION");
        intent.putExtra("resultCode", resultCode);
        intent.putExtra("data", data);
        return intent;
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
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if ("START_MEDIAPROJECTION".equals(action)) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");
            if (data != null && resultCode != -1) {
                startMediaProjection(resultCode, data);
            }
        } else if ("TAKE_SCREENSHOT".equals(action)) {
            captureScreen();
        }

        return START_NOT_STICKY;
    }

    private void startMediaProjection(int resultCode, Intent data) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, backgroundHandler);
    }

    private void captureScreen() {
        if (mediaProjection == null) {
            if (screenshotCallback != null) {
                screenshotCallback.onError("MediaProjection not ready");
            }
            return;
        }

        backgroundHandler.post(() -> {
            try {
                imageReader = ImageReader.newInstance(displayWidth, displayHeight,
                        PixelFormat.RGBA_8888, 2);

                virtualDisplay = mediaProjection.createVirtualDisplay(
                        "ScreenCapture",
                        displayWidth, displayHeight, displayDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null, backgroundHandler
                );

                // Wait for the image
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
                        if (screenshotCallback != null) {
                            screenshotCallback.onError(e.getMessage());
                        }
                    } finally {
                        if (image != null) image.close();
                    }
                }, backgroundHandler);

            } catch (Exception e) {
                Log.e(TAG, "createVirtualDisplay error", e);
                if (screenshotCallback != null) {
                    screenshotCallback.onError(e.getMessage());
                }
            }
        });
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

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, ScreenCaptureService.class);
        PendingIntent pi = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

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
