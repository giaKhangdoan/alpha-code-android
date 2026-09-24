package com.ubtrobot.mini.sdkdemo.common.handlers;

import android.support.annotation.NonNull;
import android.util.Log;

import com.ubtrobot.mini.sdkdemo.apis.SmartHomeApi;
import com.ubtrobot.mini.sdkdemo.log.LogLevel;
import com.ubtrobot.mini.sdkdemo.log.LogManager;
import com.ubtrobot.mini.sdkdemo.models.response.VoiceResponseDto;
import com.ubtrobot.mini.sdkdemo.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class SmartHomeHandler {

    private static final String TAG = "SmartHomeHandler";
    private static SmartHomeHandler instance;

    private final SmartHomeApi api;

    private final TTSHandler ttsHandler;

    public SmartHomeHandler() {
        Retrofit retrofit = ApiClient.getRobotServiceInstance();
        api = retrofit.create(SmartHomeApi.class);
        ttsHandler = TTSHandler.getInstance();
    }

    public static SmartHomeHandler getInstance() {
        if (instance == null) {
            instance = new SmartHomeHandler();
        }
        return instance;
    }

    public void smartHomeControl(
            @NonNull String id,
            @NonNull String name,
            @NonNull String message,
            @NonNull String language) {
        Call<VoiceResponseDto> call = api.smartHomeControl(id, name, message, language);

        call.enqueue(new Callback<VoiceResponseDto>() {
            @Override
            public void onResponse(Call<VoiceResponseDto> call, Response<VoiceResponseDto> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    String error = "API Error: " + response.code();
                    Log.e(TAG, error);
                    return;
                }

                VoiceResponseDto body = response.body();
                if (body.isSuccess()) {
                    ttsHandler.doTTS(body.getMessage(), language);
                    Log.d(TAG, "SmartHome API Success: " + body.getMessage());
                    LogManager.log(LogLevel.INFO, "smarthome", "SmartHome API Success: " + body.getMessage(),
                            "smarthome", name);
                } else {
                    ttsHandler.doTTS(body.getMessage(), language);
                    Log.e(TAG, "SmartHome API Failed: " + body.getMessage());
                    LogManager.log(LogLevel.ERROR, "smarthome", "SmartHome API Failed: " + body.getMessage(),
                            "smarthome", name);
                }
            }

            @Override
            public void onFailure(Call<VoiceResponseDto> call, Throwable t) {
                Log.e(TAG, "SmartHome API Failure", t);
            }
        });
    }
}
