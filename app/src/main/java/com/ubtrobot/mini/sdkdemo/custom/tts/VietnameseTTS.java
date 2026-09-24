package com.ubtrobot.mini.sdkdemo.custom.tts;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class VietnameseTTS implements TTS {
    private static final String TAG = "VietnameseTTS";
    private TextToSpeech tts;
    private boolean isReady = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, TTSCallback> callbackMap = new ConcurrentHashMap<>();
    private static VietnameseTTS instance;
    private Context context;

    private VietnameseTTS(Context context) {
        this.context = context.getApplicationContext();
        init(context);
    }

    public static synchronized VietnameseTTS getInstance(Context context) {
        if (instance == null) {
            instance = new VietnameseTTS(context);
        }
        return instance;
    }

    public static synchronized VietnameseTTS getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VietnameseTTS must be initialized first with getInstance(Context)");
        }
        return instance;
    }

    @Override
    public void init(Context context) {
        if (tts != null) {
            shutdown();
        }

        // Use Google TTS Engine explicitly for Vietnamese support
        String googleTtsEngine = "com.google.android.tts";
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                Log.i(TAG, "TTS initialized with engine: " + tts.getDefaultEngine());
                setLanguageForVietnamese();
            } else {
                isReady = false;
                Log.e(TAG, "TTS Initialization Failed: " + status);
                notifyAllCallbacks(TTSCallback::onError);
            }
        }, googleTtsEngine);

        setupProgressListener();
    }

    private void setLanguageForVietnamese() {
        Locale vietnameseLocale = new Locale("vi");
        int result = tts.setLanguage(vietnameseLocale);

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            isReady = false;
            Log.e(TAG, "Vietnamese language pack missing or not supported.");

            // Prompt user to install data
            Intent installIntent = new Intent();
            installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(installIntent);
            } catch (Exception e) {
                Log.e(TAG, "Could not launch TTS install activity", e);
            }

            notifyAllCallbacks(TTSCallback::onError);
        } else {
            isReady = true;
            Log.i(TAG, "TTS is ready for Vietnamese.");
        }
    }

    private void setupProgressListener() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                TTSCallback cb = callbackMap.get(utteranceId);
                if (cb != null)
                    mainHandler.post(cb::onStart);
            }

            @Override
            public void onDone(String utteranceId) {
                TTSCallback cb = callbackMap.remove(utteranceId);
                if (cb != null)
                    mainHandler.post(cb::onDone);
            }

            @Override
            public void onError(String utteranceId) {
                TTSCallback cb = callbackMap.remove(utteranceId);
                if (cb != null)
                    mainHandler.post(cb::onError);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                onError(utteranceId);
            }
        });
    }

    @Override
    public void stopIfPlaying() {
        if (!tts.isSpeaking())
            return;
        tts.stop();
    }

    @Override
    public void doTTS(String text) {
        doTTS(text, null);
    }

    @Override
    public void doTTS(String text, TTSCallback callback) {
        // Retry setting language if not ready
        if (!isReady && tts != null) {
            Log.i(TAG, "TTS not ready, re-trying to set language to Vietnamese...");
            setLanguageForVietnamese();
        }

        if (!isReady) {
            Log.w(TAG, "TTS still not ready yet → will skip text: " + text);
            if (callback != null) {
                mainHandler.post(callback::onError);
            }
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "No text to speak");
            if (callback != null) {
                mainHandler.post(callback::onError);
            }
            return;
        }

        String utteranceId = UUID.randomUUID().toString();
        if (callback != null) {
            callbackMap.put(utteranceId, callback);
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    @Override
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        callbackMap.clear();
        isReady = false;
    }

    @Override
    public boolean isReady() {
        return isReady;
    }

    public String debugTTSInfo() {
        StringBuilder sb = new StringBuilder();
        if (tts == null)
            return "TTS chưa khởi tạo";

        // 1. Check Engine hiện tại
        String currentEngine = tts.getDefaultEngine();
        sb.append("Engine đang dùng: ").append(currentEngine).append("\n");

        // 2. Check danh sách Engines
        try {
            java.util.List<TextToSpeech.EngineInfo> engines = tts.getEngines();
            sb.append("DS Engine cài đặt:\n");
            for (TextToSpeech.EngineInfo engine : engines) {
                sb.append("- ").append(engine.label).append(" (").append(engine.name).append(")\n");
            }
        } catch (Exception e) {
            sb.append("Lỗi lấy DS Engine: ").append(e.getMessage()).append("\n");
        }

        // 3. Check trạng thái Tiếng Việt
        Locale vi = new Locale("vi");
        int status = tts.isLanguageAvailable(vi);
        sb.append("Trạng thái Tiếng Việt: ");
        switch (status) {
            case TextToSpeech.LANG_AVAILABLE:
                sb.append("Sẵn sàng (Available)");
                break;
            case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                sb.append("Sẵn sàng (Country Available)");
                break;
            case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                sb.append("Sẵn sàng (Var Available)");
                break;
            case TextToSpeech.LANG_MISSING_DATA:
                sb.append("Thiếu dữ liệu (Cần tải về)");
                break;
            case TextToSpeech.LANG_NOT_SUPPORTED:
                sb.append("Không hỗ trợ");
                break;
            default:
                sb.append("Mã lỗi: ").append(status);
        }

        return sb.toString();
    }

    private void notifyAllCallbacks(Consumer<TTSCallback> action) {
        for (TTSCallback callback : callbackMap.values()) {
            mainHandler.post(() -> action.accept(callback));
        }
        callbackMap.clear();
    }
}