package com.fangyi.translator.engine;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Android built-in SpeechRecognizer wrapper. Supports offline English recognition
 * when the user has downloaded the English offline speech pack from Google settings.
 */
public class SpeechEngine {

    private static final String TAG = "SpeechEngine";

    private SpeechRecognizer speechRecognizer;
    private final Intent recognizerIntent;
    private boolean isListening = false;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private Callback callback;

    public interface Callback {
        void onPartialResult(String text);
        void onFinalResult(String text);
        void onError(String error);
        void onReadyForSpeech();
    }

    public SpeechEngine(Context context) {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        // Prefer offline
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (callback != null) callback.onReadyForSpeech();
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                if (callback == null) return;
                String msg;
                switch (error) {
                    case SpeechRecognizer.ERROR_NETWORK:
                        msg = "网络错误";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        msg = "网络超时";
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        msg = "";
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        msg = "语音超时";
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        msg = "录音权限未授予";
                        break;
                    default:
                        msg = "识别错误: " + error;
                        break;
                }
                if (!msg.isEmpty()) callback.onError(msg);
                restartListening();
            }

            @Override
            public void onResults(Bundle results) {
                if (callback != null) {
                    String text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            .get(0);
                    callback.onFinalResult(text);
                }
                restartListening();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                if (callback != null) {
                    String text = partialResults.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION).get(0);
                    callback.onPartialResult(text);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void startListening() {
        if (!isListening) {
            isListening = true;
            executor.execute(() -> {
                try {
                    speechRecognizer.startListening(recognizerIntent);
                } catch (Exception e) {
                    Log.e(TAG, "startListening error", e);
                    if (callback != null) callback.onError("启动语音识别失败");
                }
            });
        }
    }

    private void restartListening() {
        if (isListening) {
            executor.execute(() -> {
                try {
                    speechRecognizer.startListening(recognizerIntent);
                } catch (Exception e) {
                    Log.e(TAG, "restartListening error", e);
                }
            });
        }
    }

    public void stopListening() {
        isListening = false;
        executor.execute(() -> {
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {}
        });
    }

    public void destroy() {
        stopListening();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        callback = null;
    }
}
