package com.ubtrobot.mini.sdkdemo.speech;

import android.util.Log;

import com.ubtechinc.mini.weinalib.wakeup.WeiNaWakeUpHelper;
import com.ubtrobot.master.service.MasterSystemService;
import com.ubtrobot.mini.sdkdemo.MainActivity;

public class DemoMasterService extends MasterSystemService {
  @Override protected void onServiceCreate() {
    super.onServiceCreate();
    Log.i(MainActivity.TAG, "Master: Master started");
    //If you want to use the WeiNa wake-up module(wakeup-5.0.0.aar), please call the initialization method first
    // The initialization of the wake-up module requires an available network
    // WeiNaWakeUpHelper.get().initialize();

    //init speech modules
    DemoSpeechJava.getInstance().init(this);
    Log.i(MainActivity.TAG, getStates().toString());
  }

  @Override protected void onServiceDestroy() {
    super.onServiceDestroy();
  }
}
