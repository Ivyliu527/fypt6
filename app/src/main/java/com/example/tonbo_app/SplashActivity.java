package com.example.tonbo_app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private ImageView logoImage;
    private TextView appNameText;
    private TextView sloganText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        logoImage = findViewById(R.id.logoImage);
        appNameText = findViewById(R.id.appNameText);
        sloganText = findViewById(R.id.sloganText);

        // 強制使用英文文本，無論系統語言設置
        appNameText.setText("Tonbo");
        sloganText.setText("Your Visual Partner");
        logoImage.setContentDescription("Tonbo app logo");

        // 啟動動畫
        startEntranceAnimation();

        // 3秒後檢查登入狀態並跳轉
        new Handler().postDelayed(() -> {
            checkLoginStatusAndNavigate();
        }, 3000);
    }
    
    private void checkLoginStatusAndNavigate() {
        String role = RolePreferences.getRole(this);
        if (role == null || role.isEmpty()) {
            startActivity(new Intent(SplashActivity.this, RoleSelectionActivity.class));
            finish();
            return;
        }
        if (RolePreferences.ROLE_VOLUNTEER.equals(role)) {
            startActivity(new Intent(SplashActivity.this, VolunteerMainActivity.class));
            finish();
            return;
        }

        // 視障用戶：沿用原有手勢登入 / 主頁邏輯
        boolean gestureLoginEnabled = GestureManagementActivity.isGestureLoginEnabled(this);
        Intent intent;
        if (gestureLoginEnabled) {
            intent = new Intent(SplashActivity.this, GestureInputActivity.class);
        } else {
            intent = new Intent(SplashActivity.this, MainActivity.class);
        }
        startActivity(intent);
        finish();
    }

    private void startEntranceAnimation() {
        // Logo 動畫：從透明到不透明 + 從下方進入
        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(logoImage, "alpha", 0f, 1f);
        ObjectAnimator logoTranslationY = ObjectAnimator.ofFloat(logoImage, "translationY", 100f, 0f);

        // 應用名稱動畫：從透明到不透明 + 輕微放大
        ObjectAnimator nameAlpha = ObjectAnimator.ofFloat(appNameText, "alpha", 0f, 1f);
        ObjectAnimator nameScaleX = ObjectAnimator.ofFloat(appNameText, "scaleX", 0.8f, 1f);
        ObjectAnimator nameScaleY = ObjectAnimator.ofFloat(appNameText, "scaleY", 0.8f, 1f);

        // 口號動畫：延遲出現 + 從透明到不透明
        ObjectAnimator sloganAlpha = ObjectAnimator.ofFloat(sloganText, "alpha", 0f, 1f);

        // 設置動畫時長
        logoAlpha.setDuration(1000);
        logoTranslationY.setDuration(1000);
        nameAlpha.setDuration(800);
        nameScaleX.setDuration(800);
        nameScaleY.setDuration(800);
        sloganAlpha.setDuration(600);

        // 創建動畫集合
        AnimatorSet animatorSet = new AnimatorSet();

        // 第一階段：Logo 和應用名稱同時動畫
        AnimatorSet firstStage = new AnimatorSet();
        firstStage.playTogether(logoAlpha, logoTranslationY, nameAlpha, nameScaleX, nameScaleY);

        // 第二階段：口號延遲出現
        AnimatorSet secondStage = new AnimatorSet();
        secondStage.play(sloganAlpha).after(500); // 延遲500毫秒

        // 播放所有動畫
        animatorSet.playTogether(firstStage, secondStage);
        animatorSet.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 防止返回時重新顯示動畫
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}

