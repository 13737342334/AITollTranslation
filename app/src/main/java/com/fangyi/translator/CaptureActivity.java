package com.fangyi.translator;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import com.fangyi.translator.service.ScreenCaptureService;

public class CaptureActivity extends Activity {

    private static final int REQUEST_MEDIA_PROJECTION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep this activity as small and invisible as possible
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mpm == null) {
            Toast.makeText(this, "当前设备不支持屏幕截图", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            Intent si = new Intent(this, ScreenCaptureService.class);
            si.setAction(ScreenCaptureService.ACTION_START_CAPTURE);
            si.putExtra("resultCode", resultCode);
            si.putExtra("data", data);
            startForegroundService(si);
        } else if (resultCode != RESULT_OK) {
            Toast.makeText(this, "已取消截图翻译", Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    @Override
    public void finish() {
        super.finish();
        // No animation - seamless return to previous app
        overridePendingTransition(0, 0);
    }
}
