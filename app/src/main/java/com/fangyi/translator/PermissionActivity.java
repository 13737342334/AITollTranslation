package com.fangyi.translator;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fangyi.translator.service.FloatingBallService;
import com.fangyi.translator.util.PermissionHelper;

/**
 * Onboarding flow: 3 introduction screens + permission setup.
 * Shown on first launch, skippable.
 */
public class PermissionActivity extends AppCompatActivity {

    private static final int[] TITLES = {
            R.string.onboarding_title_1,
            R.string.onboarding_title_2,
            R.string.onboarding_title_3
    };
    private static final int[] DESCS = {
            R.string.onboarding_desc_1,
            R.string.onboarding_desc_2,
            R.string.onboarding_desc_3
    };

    private TextView tvTitle, tvDesc;
    private View dot1, dot2, dot3;
    private Button btnAction, btnSkip;
    private int currentPage = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        tvTitle = findViewById(R.id.tv_onboarding_title);
        tvDesc = findViewById(R.id.tv_onboarding_desc);
        dot1 = findViewById(R.id.dot_1);
        dot2 = findViewById(R.id.dot_2);
        dot3 = findViewById(R.id.dot_3);
        btnAction = findViewById(R.id.btn_onboarding_action);
        btnSkip = findViewById(R.id.btn_skip);

        updatePage();

        btnAction.setOnClickListener(v -> {
            if (currentPage < 2) {
                currentPage++;
                updatePage();
            } else {
                // Last page: go grant permissions
                startMainFlow();
            }
        });

        btnSkip.setOnClickListener(v -> startMainFlow());
    }

    private void updatePage() {
        tvTitle.setText(TITLES[currentPage]);
        tvDesc.setText(DESCS[currentPage]);

        dot1.setBackgroundResource(currentPage == 0
                ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
        dot2.setBackgroundResource(currentPage == 1
                ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
        dot3.setBackgroundResource(currentPage == 2
                ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);

        if (currentPage == 2) {
            btnAction.setText(R.string.btn_get_started);
        } else {
            btnAction.setText(R.string.next);
        }
    }

    private void startMainFlow() {
        // Check overlay permission first - most critical
        if (!PermissionHelper.hasOverlayPermission(this)) {
            PermissionHelper.openOverlaySettings(this);
        }

        // Start main activity
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
