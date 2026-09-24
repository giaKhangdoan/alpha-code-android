package com.ubtrobot.mini.sdkdemo.custom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ubtrobot.masterevent.protos.SysMasterEvent;
import com.ubtrobot.mini.sdkdemo.common.handlers.TTSHandler;
import com.ubtrobot.mini.sysevent.EventApi;
import com.ubtrobot.mini.sysevent.SysEventApi;

public class BatteryReceiver extends BroadcastReceiver {
    private EventApi sys;

    @Override
    public void onReceive(Context context, Intent intent) {
        sys = SysEventApi.get();
        SysMasterEvent.BatteryStatusData data = sys.getCurrentBatteryInfoSync();
        int level = data.getLevel();
        if (level <= 20) {
            TTSHandler tts = TTSHandler.getInstance();
            tts.doTTS("Battery low, please charge me", "en");

        }
    }
}