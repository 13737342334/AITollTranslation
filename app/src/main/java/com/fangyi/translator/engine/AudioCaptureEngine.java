package com.fangyi.translator.engine;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures internal audio playback using AudioPlaybackCapture API (Android 10+).
 * Falls back to mic capture if internal audio is unavailable.
 */
public class AudioCaptureEngine {

    private static final String TAG = "AudioCapture";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = 4096;

    private AudioRecord audioRecord;
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);
    private final Executor executor = Executors.newSingleThreadExecutor();
    private MediaProjection mediaProjection;
    private Callback callback;

    public interface Callback {
        void onAudioData(byte[] buffer, int bytesRead);
        void onCaptureError(String error);
        void onCaptureStarted();
        void onCaptureStopped();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    /**
     * Start capturing internal audio. Requires AudioPlaybackCapture permission
     * and a valid MediaProjection.
     */
    public void startCapture() {
        if (isCapturing.get()) return;

        executor.execute(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                    startInternalAudioCapture();
                } else {
                    startMicFallback();
                }
                isCapturing.set(true);
                if (callback != null) callback.onCaptureStarted();
                startReading();
            } catch (SecurityException e) {
                Log.e(TAG, "Audio capture permission denied, falling back to mic", e);
                try {
                    startMicFallback();
                    isCapturing.set(true);
                    if (callback != null) callback.onCaptureStarted();
                    startReading();
                } catch (Exception ex) {
                    if (callback != null) callback.onCaptureError("音频捕获失败");
                }
            } catch (Exception e) {
                Log.e(TAG, "startCapture error", e);
                if (callback != null) callback.onCaptureError("音频捕获失败: " + e.getMessage());
            }
        });
    }

    private void startInternalAudioCapture() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        AudioPlaybackCaptureConfiguration config =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build();

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int bufferSize = Math.max(minBufferSize * 2, BUFFER_SIZE);

        audioRecord = new AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();

        audioRecord.startRecording();
    }

    private void startMicFallback() throws Exception {
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int bufferSize = Math.max(minBufferSize * 2, BUFFER_SIZE);

        audioRecord = new AudioRecord.Builder()
                .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();

        audioRecord.startRecording();
    }

    private void startReading() {
        executor.execute(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (isCapturing.get() && audioRecord != null) {
                try {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && callback != null) {
                        callback.onAudioData(buffer.clone(), bytesRead);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "read error", e);
                    if (callback != null) callback.onCaptureError("音频读取错误");
                }
            }
        });
    }

    public void stopCapture() {
        isCapturing.set(false);
        executor.execute(() -> {
            try {
                if (audioRecord != null) {
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "stopCapture error", e);
            }
            if (callback != null) callback.onCaptureStopped();
        });
    }

    public boolean isCapturing() {
        return isCapturing.get();
    }

    public void destroy() {
        stopCapture();
        mediaProjection = null;
    }
}
