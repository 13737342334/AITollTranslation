package com.fangyi.translator;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.fangyi.translator.data.AppDatabase;
import com.fangyi.translator.engine.TranslationEngine;
import com.fangyi.translator.util.PreferencesHelper;

public class App extends Application {

    public static final String CHANNEL_FLOATING_BALL = "floating_ball";
    public static final String CHANNEL_TRANSLATION = "translation_status";

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannels();
        PreferencesHelper.init(this);
        AppDatabase.init(this);
        TranslationEngine.init(this);
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;

            CharSequence name = "悬浮翻译助手";
            NotificationChannel floatingChannel = new NotificationChannel(
                    CHANNEL_FLOATING_BALL,
                    name,
                    NotificationManager.IMPORTANCE_MIN
            );
            floatingChannel.setDescription("悬浮球常驻通知");
            nm.createNotificationChannel(floatingChannel);

            NotificationChannel translationChannel = new NotificationChannel(
                    CHANNEL_TRANSLATION,
                    "翻译状态",
                    NotificationManager.IMPORTANCE_LOW
            );
            translationChannel.setDescription("翻译进行中的状态通知");
            nm.createNotificationChannel(translationChannel);
        }
    }
}
