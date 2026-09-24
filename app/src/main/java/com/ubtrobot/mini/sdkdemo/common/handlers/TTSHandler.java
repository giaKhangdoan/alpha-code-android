package com.ubtrobot.mini.sdkdemo.common.handlers;

import android.content.Context;
import android.util.Log;

import com.ubtrobot.commons.Priority;
import com.ubtrobot.mini.sdkdemo.custom.tts.EnglishTTS;
import com.ubtrobot.mini.sdkdemo.custom.tts.TTSCallback;
import com.ubtrobot.mini.sdkdemo.custom.tts.VietnameseTTS;
import com.ubtrobot.mini.sdkdemo.log.LogLevel;
import com.ubtrobot.mini.sdkdemo.log.LogManager;
import com.ubtrobot.mini.voice.VoicePool;

public class TTSHandler {
    private static final String TAG = "TTSHandler";
    private static TTSHandler instance;
    private static EnglishTTS englishTTS;
    private static VietnameseTTS vietnameseTTS;

    private TTSHandler() {
        // Private constructor for singleton
    }

    public static synchronized TTSHandler getInstance() {
        if (instance == null) {
            instance = new TTSHandler();
        }
        return instance;
    }

    private static TTSCallback defaultCallback(String text) {
        return new TTSCallback() {
            @Override
            public void onStart() {
                Log.i(TAG, "TTS started: " + text);
                // Log tự động có accountLessonId nếu đang trong submission
                LogManager.log(LogLevel.INFO, "speech", "TTS started: " + text, "speech", null);
            }

            @Override
            public void onDone() {
                Log.i(TAG, "After TTS successfully");
            }

            @Override
            public void onError() {
                Log.e(TAG, "Error playing TTS: " + text);
                LogManager.log(LogLevel.ERROR, "speech", "Error playing TTS: " + text, "speech", null);
            }
        };
    }

    public void init(Context context) {
        englishTTS = EnglishTTS.getInstance(context);
        vietnameseTTS = VietnameseTTS.getInstance(context);
        Log.i(TAG, "Done init");
    }

    public void doTTS(String text, String lang) {
        if (text == null)
            return;
        if (lang.equals("en")) {
            englishTTS.doTTS(text, defaultCallback(text));
        } else {
            vietnameseTTS.doTTS(text, defaultCallback(text));
        }
    }

    public void doTTS(String text, String lang, TTSCallback callback) {
        if (text == null)
            return;
        TTSCallback actualCallback = callback != null ? callback : defaultCallback(text);
        if (lang.equals("en")) {
            englishTTS.doTTS(text, actualCallback);
        } else {
            vietnameseTTS.doTTS(text, actualCallback);
        }
    }

    public void stopIfPlaying() {
        if (englishTTS != null)
            englishTTS.stopIfPlaying();
        if (vietnameseTTS != null)
            vietnameseTTS.stopIfPlaying();
    }

    /**
     * Alias for stopIfPlaying - called from RobotCommandHandler
     */
    public void stop() {
        stopIfPlaying();
        Log.i(TAG, "TTS stopped");
    }
}
