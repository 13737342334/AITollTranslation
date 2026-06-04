package com.fangyi.translator;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.fangyi.translator.service.ScreenCaptureService;

/**
 * Transparent Activity that requests MediaProjection permission
 * and starts ScreenCaptureService with the result.
 */
public class CaptureActivity extends Activity {

    private static final String TAG = "CaptureActivity";
    private static final int REQUEST_MEDIA_PROJECTION = 1001;

    private MediaProjectionManager mpm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mpm == null) {
            Toast.makeText(this, "当前设备不支持屏幕截图", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent captureIntent = mpm.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // Start ScreenCaptureService with the MediaProjection data
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.setAction(ScreenCaptureService.ACTION_START_CAPTURE);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                startForegroundService(serviceIntent);

                Toast.makeText(this, "正在截图翻译...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要屏幕截图权限才能翻译页面", Toast.LENGTH_SHORT).show();
            }
        }

        finish();
    }
}
