package com.ubtrobot.mini.sdkdemo;

import android.app.Application;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.msc.util.log.DebugLog;
import com.ubtech.utilcode.utils.Utils;
import com.ubtech.utilcode.utils.thread.ThreadPool;
import com.ubtrobot.master.log.InfrequentLoggerFactory;
import com.ubtrobot.mini.SDKInit;
import com.ubtrobot.mini.properties.sdk.Path;
import com.ubtrobot.mini.properties.sdk.PropertiesApi;
import com.ubtrobot.mini.sdkdemo.common.handlers.TTSHandler;
import com.ubtrobot.mini.sdkdemo.log.LogManager;
import com.ubtrobot.mini.sdkdemo.socket.RobotSocketClient;
import com.ubtrobot.mini.sdkdemo.socket.RobotSocketController;
import com.ubtrobot.mini.sdkdemo.speech.DemoMasterService;
import com.ubtrobot.mini.sdkdemo.speech.DemoSpeechJava;
import com.ubtrobot.service.ServiceModules;
import com.ubtrobot.speech.SpeechService;
import com.ubtrobot.speech.SpeechSettings;
import com.ubtrobot.ulog.FwLoggerFactory2;
import com.ubtrobot.ulog.logger.android.AndroidLoggerFactory;

public class DemoApp extends Application {

    public static final String TAG = "API_TAG";

    @Override
    public void onCreate() {
        super.onCreate();
        TTSHandler.getInstance().init(Utils.getContext().getApplicationContext());
        PropertiesApi.setRootPath(Path.DIR_MINI_FILES_SDCARD_ROOT);
        SDKInit.initialize(this);

        // Initialize LogManager for remote logging
        LogManager.init();
        Log.i(TAG, "LogManager initialized");

        initSpeech();
    }

    private void initSpeech() {
        StringBuffer param = new StringBuffer();
        try {
            param.append("appid=").append(getString(R.string.app_id));
            param.append(",");
            param.append(SpeechConstant.ENGINE_MODE + "=" + SpeechConstant.MODE_MSC);
            SpeechUtility.createUtility(this, param.toString());
            DebugLog.setLogLevel(DebugLog.LOG_LEVEL.none);
            FwLoggerFactory2.setup(
                    BuildConfig.DEBUG ? new AndroidLoggerFactory() : new InfrequentLoggerFactory());
            startService(new Intent(this, DemoMasterService.class));

            Log.i(TAG, "Speech App: Declaring speech settings");
            ServiceModules.initialize(this);
            ServiceModules.declare(SpeechSettings.class,
                    (aClass, moduleCreatedNotifier) -> {
                        moduleCreatedNotifier.notifyModuleCreated(
                                DemoSpeechJava.getInstance().createSpeechSettings());
                    });
            Log.i(TAG, "Speech App: Declaring speech service");
            ServiceModules.declare(SpeechService.class,
                    (aClass, moduleCreatedNotifier) -> ThreadPool.runOnNonUIThread(() -> {
                        while (DemoSpeechJava.getInstance().createSpeechService() == null) {
                            Log.i(TAG, "Speech App: Cannot get service");
                            SystemClock.sleep(5);
                        }
                        moduleCreatedNotifier.notifyModuleCreated(DemoSpeechJava.getInstance().createSpeechService());
                    }));
            Log.d(TAG, "Speech App: Initialization complete");
        } catch (Exception e) {
            Log.i(TAG, "Error: " + e);
        }
    }

    // @Override
    // protected void onStartFailed(UbtSkillInfo ubtSkillInfo) {
    //
    // }
    //
    // @Override
    // protected void onInterrupted() {
    //
    // }

}
