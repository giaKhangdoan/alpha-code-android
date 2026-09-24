package com.ubtrobot.mini.sdkdemo.common.handlers;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ubtrobot.action.ActionApi;
import com.ubtrobot.mini.sdkdemo.activity.TakePictureActivity;
import com.ubtrobot.mini.sdkdemo.custom.tts.TTSCallback;
import com.ubtrobot.mini.sdkdemo.log.LogLevel;
import com.ubtrobot.mini.sdkdemo.log.LogManager;

public class CameraHandler {
    private static final String TAG = "CameraHandler";
    private TakePictureActivity takePictureActivity;
    private ActionApi actionApi;
    private TTSHandler tts;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public CameraHandler() {
        this.takePictureActivity = TakePictureActivity.get();
        this.actionApi = ActionApi.get();
        this.tts = TTSHandler.getInstance();
    }

    public void handleQRCode(String text, String lang) {
        // Use default message if text is null or empty
        final String message = (text == null || text.trim().isEmpty())
                ? "Please show the QR code in front of me to take a picture. Now I will take the picture."
                : text;

        // Call TTS
        tts.doTTS(message, lang, new TTSCallback() {
            @Override
            public void onStart() {
                Log.i(TAG, "TTS started: " + message);
                LogManager.log(LogLevel.INFO, TAG, "TTS started: " + message);
            }

            @Override
            public void onDone() {
                Log.i(TAG, "Voice playback finished successfully");
                // Only take picture if a QR code text was provided
                if (!message.trim().isEmpty()) {
                    takePictureActivity.takePicImmediately("qr-code", lang);
                }
            }

            @Override
            public void onError() {
                Log.e(TAG, "Error playing TTS: " + message);
                LogManager.log(LogLevel.ERROR, TAG, "Error playing TTS: " + message);
            }
        });
    }

    public void handleVideoGenerate(String text, String lang) {
        final String message = (text == null || text.trim().isEmpty())
                ? "Please place the picture under my feet in my view. Now I will bend down to capture it."
                : text;

        tts.doTTS(message, lang, new TTSCallback() {
            @Override
            public void onStart() {
                Log.i(TAG, "TTS started: " + message);
                LogManager.log(LogLevel.ERROR, TAG, "TTS started: " + message);
            }

            @Override
            public void onDone() {
                Log.i(TAG, "After voice played successfully");
                actionApi.playCustomizeAction("takelowpic", null);

                handler.postDelayed(() -> {
                    takePictureActivity.takePicImmediately("video-capture", lang);
                }, 3000); // Delay 3 seconds before taking picture
            }

            @Override
            public void onError() {
                Log.e(TAG, "Error playing TTS: " + message);
                LogManager.log(LogLevel.ERROR, TAG, "Error playing TTS: " + message);
            }
        });
    }

    public void handleOsmoCard(String text, String lang) {
        final String message = (text == null || text.trim().isEmpty())
                ? "Please place the OSMO card under my feet in my view. Now I will bend down to scan it."
                : text;

        tts.doTTS(message, lang, new TTSCallback() {
            @Override
            public void onStart() {
                Log.i(TAG, "TTS started: " + message);
                LogManager.log(LogLevel.ERROR, TAG, "TTS started: " + message);
            }

            @Override
            public void onDone() {
                Log.i(TAG, "After voice played successfully");
                actionApi.playCustomizeAction("takelowpic", null);

                handler.postDelayed(() -> {
                    takePictureActivity.takePicImmediately("osmo-card", lang);
                }, 3000); // Delay 3 seconds before taking picture
            }

            @Override
            public void onError() {
                Log.e(TAG, "Error playing TTS: " + message);
                LogManager.log(LogLevel.ERROR, TAG, "Error playing TTS: " + message);
            }
        });
    }
}
