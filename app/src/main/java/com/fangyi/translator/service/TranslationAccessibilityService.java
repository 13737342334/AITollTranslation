package com.fangyi.translator.service;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.lang.reflect.Method;

public class TranslationAccessibilityService extends AccessibilityService {

    private static final String TAG = "A11y";
    private static TranslationAccessibilityService instance;
    private static ScreenshotCallback pendingCallback;

    public interface ScreenshotCallback {
        void onScreenshot(Bitmap bitmap);
        void onError(String error);
    }

    public static boolean isEnabled() {
        return instance != null;
    }

    public static void requestScreenshot(ScreenshotCallback callback) {
        if (instance == null) {
            callback.onError("无障碍服务未开启！请前往：设置→无障碍→悬浮翻译助手→开启");
            return;
        }
        pendingCallback = callback;
        try {
            instance.captureScreen();
        } catch (Exception e) {
            callback.onError("截图异常: " + e.toString());
        }
    }

    private void captureScreen() {
        try {
            takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult result) {
                            try {
                                // Use reflection to get bitmap data (API varies by version)
                                Bitmap bitmap = extractBitmap(result);
                                if (pendingCallback != null) {
                                    if (bitmap != null) {
                                        pendingCallback.onScreenshot(bitmap);
                                    } else {
                                        pendingCallback.onError("截图数据为空");
                                    }
                                }
                            } catch (Exception e) {
                                if (pendingCallback != null) {
                                    pendingCallback.onError("截图处理异常: " + e.getMessage());
                                }
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            if (pendingCallback != null) {
                                pendingCallback.onError("截图失败(错误码:" + errorCode + ")");
                            }
                        }
                    });
        } catch (NoSuchMethodError | Exception e) {
            if (pendingCallback != null) {
                pendingCallback.onError("截图API不可用: " + e.getMessage());
            }
        }
    }

    private Bitmap extractBitmap(ScreenshotResult result) {
        // Try getHardwareBuffer first (API 34+)
        try {
            Method m = result.getClass().getMethod("getHardwareBuffer");
            Object hwb = m.invoke(result);
            if (hwb != null) {
                return Bitmap.wrapHardwareBuffer(
                        (android.hardware.HardwareBuffer) hwb,
                        null);
            }
        } catch (Exception ignored) {}

        // Try getParcelFileDescriptor (API 34+)
        try {
            Method m = result.getClass().getMethod("getParcelFileDescriptor");
            ParcelFileDescriptor pfd = (ParcelFileDescriptor) m.invoke(result);
            if (pfd != null) {
                Bitmap bmp = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                pfd.close();
                return bmp;
            }
        } catch (Exception ignored) {}

        return null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "A11yService connected");
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(this, "无障碍服务已就绪", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }
}
