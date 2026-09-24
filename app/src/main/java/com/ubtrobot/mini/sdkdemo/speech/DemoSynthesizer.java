package com.ubtrobot.mini.sdkdemo.speech;

import android.util.Log;
import com.ubtrobot.mini.sdkdemo.MainActivity;
import com.ubtrobot.mini.sdkdemo.custom.tts.EnglishTTS;
import com.ubtrobot.mini.sdkdemo.custom.tts.VietnameseTTS;
import com.ubtrobot.mini.sdkdemo.custom.tts.TTSCallback;
import com.ubtrobot.speech.AbstractSynthesizer;
import com.ubtrobot.speech.SpeakingVoice;
import com.ubtrobot.speech.SynthesisOption;
import java.util.List;

public class DemoSynthesizer extends AbstractSynthesizer {

    public DemoSynthesizer() {
    }

    @Override
    protected void startSynthesizing(SynthesisOption synthesisOption) {
        VietnameseTTS.getInstance().doTTS(synthesisOption.getInputText(), new TTSCallback() {
            @Override
            public void onStart() {

            }

            @Override
            public void onDone() {
                resolveSynthesizing();
            }

            @Override
            public void onError() {

            }
        });
    }

    @Override
    protected void stopSynthesizing() {
        Log.i(MainActivity.TAG, "Synthesizer: Stop");
    }

    @Override
    public List<SpeakingVoice> getSpeakingVoiceList() {
        Log.i(MainActivity.TAG, "Synthesizer: Get voice list");
        return null;
    }
}
