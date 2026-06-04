package com.fangyi.translator;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.fangyi.translator.service.FloatingBallService;
import com.fangyi.translator.util.PermissionHelper;

public class MainActivity extends AppCompatActivity {

    private Button btnToggleService;
    private TextView tvStatusText;
    private View indicatorStatus;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startFloatingBallService();
                } else {
                    Toast.makeText(this, "需要通知权限才能保持悬浮球运行", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggleService = findViewById(R.id.btn_toggle_service);
        tvStatusText = findViewById(R.id.tv_status_text);
        indicatorStatus = findViewById(R.id.indicator_status);

        updateServiceStatus();

        btnToggleService.setOnClickListener(v -> {
            if (FloatingBallService.isRunning()) {
                stopFloatingBallService();
            } else {
                checkAndStartService();
            }
        });

        if (!PermissionHelper.hasOverlayPermission(this)) {
            showOverlayPermissionDialog();
        }
    }

    private void checkAndStartService() {
        if (!PermissionHelper.hasOverlayPermission(this)) {
            showOverlayPermissionDialog();
            return;
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS");
                return;
            }
        }

        startFloatingBallService();
    }

    private void startFloatingBallService() {
        Intent intent = new Intent(this, FloatingBallService.class);
        ContextCompat.startForegroundService(this, intent);
        updateServiceStatus();
        Toast.makeText(this, "悬浮球已启动", Toast.LENGTH_SHORT).show();
    }

    private void stopFloatingBallService() {
        Intent intent = new Intent(this, FloatingBallService.class);
        stopService(intent);
        updateServiceStatus();
        Toast.makeText(this, "悬浮球已停止", Toast.LENGTH_SHORT).show();
    }

    private void showOverlayPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("需要悬浮窗权限")
                .setMessage("悬浮翻译助手需要在其他应用上方显示悬浮球和翻译内容。\n\n请授予「在其他应用上层显示」权限。")
                .setPositiveButton("去授权", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
    }

    private void updateServiceStatus() {
        boolean running = FloatingBallService.isRunning();
        tvStatusText.setText(running ? "悬浮球：运行中" : "悬浮球：已停止");
        btnToggleService.setText(running ? R.string.btn_stop_service : R.string.btn_start_service);
        indicatorStatus.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        running ? getColor(R.color.success) : getColor(R.color.on_surface_secondary)
                ));
    }
}
