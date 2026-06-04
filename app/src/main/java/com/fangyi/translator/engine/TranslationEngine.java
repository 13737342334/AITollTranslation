package com.fangyi.translator.engine;

import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ML Kit Translation wrapper. Offline models ~30MB for EN<->ZH.
 */
public class TranslationEngine {

    private static TranslationEngine instance;
    private Translator translator;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private boolean modelReady = false;

    private TranslationEngine() {}

    public static void init(Context context) {
        if (instance == null) {
            instance = new TranslationEngine();
            instance.initialize();
        }
    }

    public static TranslationEngine getInstance() {
        return instance;
    }

    private void initialize() {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.CHINESE)
                .build();
        translator = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> modelReady = true)
                .addOnFailureListener(e -> modelReady = false);
    }

    public boolean isModelReady() {
        return modelReady;
    }

    /**
     * Translates English text to Chinese. Returns original text if model not ready.
     */
    public Task<String> translate(String englishText) {
        if (!modelReady || translator == null) {
            TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
            tcs.setResult("[模型未就绪] " + englishText);
            return tcs.getTask();
        }

        return translator.translate(englishText)
                .continueWith(executor, task -> {
                    if (task.isSuccessful()) {
                        return task.getResult();
                    }
                    return "[翻译失败] " + englishText;
                });
    }

    public void close() {
        if (translator != null) {
            try {
                translator.close();
            } catch (Exception ignored) {}
        }
    }
}
