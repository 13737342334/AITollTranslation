package com.fangyi.translator.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesHelper {

    private static final String PREFS_NAME = "fangyi_translator_prefs";
    private static final String KEY_SOURCE_LANG = "source_language";
    private static final String KEY_TARGET_LANG = "target_language";
    private static final String KEY_TEXT_SIZE = "text_size";
    private static final String KEY_OVERLAY_OPACITY = "overlay_opacity";
    private static final String KEY_AUTO_TRANSLATE = "auto_translate";
    private static final String KEY_LOW_BATTERY_STOP = "low_battery_stop";
    private static final String KEY_FIRST_LAUNCH = "first_launch";

    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences getPrefs() {
        if (prefs == null) {
            throw new IllegalStateException("PreferencesHelper not initialized");
        }
        return prefs;
    }

    public static String getSourceLanguage() {
        return getPrefs().getString(KEY_SOURCE_LANG, "en");
    }

    public static String getTargetLanguage() {
        return getPrefs().getString(KEY_TARGET_LANG, "zh");
    }

    public static int getTextSize() {
        return getPrefs().getInt(KEY_TEXT_SIZE, 14);
    }

    public static int getOverlayOpacity() {
        return getPrefs().getInt(KEY_OVERLAY_OPACITY, 60);
    }

    public static boolean isAutoTranslateEnabled() {
        return getPrefs().getBoolean(KEY_AUTO_TRANSLATE, false);
    }

    public static boolean isLowBatteryStopEnabled() {
        return getPrefs().getBoolean(KEY_LOW_BATTERY_STOP, true);
    }

    public static boolean isFirstLaunch() {
        boolean first = getPrefs().getBoolean(KEY_FIRST_LAUNCH, true);
        if (first) {
            getPrefs().edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
        }
        return first;
    }

    public static void setSourceLanguage(String lang) {
        getPrefs().edit().putString(KEY_SOURCE_LANG, lang).apply();
    }

    public static void setTargetLanguage(String lang) {
        getPrefs().edit().putString(KEY_TARGET_LANG, lang).apply();
    }

    public static void setTextSize(int sizeSp) {
        getPrefs().edit().putInt(KEY_TEXT_SIZE, sizeSp).apply();
    }

    public static void setOverlayOpacity(int percent) {
        getPrefs().edit().putInt(KEY_OVERLAY_OPACITY, percent).apply();
    }

    public static void setAutoTranslateEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply();
    }

    public static void setLowBatteryStopEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_LOW_BATTERY_STOP, enabled).apply();
    }
}
