package com.ubtrobot.mini.sdkdemo.common.handlers;

import android.support.annotation.NonNull;
import android.util.Log;

import com.ubtechinc.sauron.api.FaceApi;
import com.ubtechinc.sauron.api.FaceInfo;
import com.ubtrobot.commons.ResponseListener;
import com.ubtrobot.mini.sdkdemo.custom.tts.TTSCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FaceHandler {
    private static final String TAG = "FaceHandler";
    private final FaceApi faceApi = FaceApi.get();
    private TTSHandler tts = TTSHandler.getInstance();
    private static FaceHandler instance;
    private static String currentUId = "";

    public static FaceHandler get() {
        if (instance == null) {
            instance = new FaceHandler();
        }
        return instance;
    }

    public void handleDetect(String lang) {
        tts.doTTS(lang.equals("en") ? "Ok, let me see you" : "Được rồi, để tôi nhìn bạn nào", lang, new TTSCallback() {
            @Override
            public void onStart() {

            }

            @Override
            public void onDone() {
                faceApi.faceRecognize(10000, new ResponseListener<List<FaceInfo>>() {
                    @Override
                    public void onResponseSuccess(List<FaceInfo> faceInfos) {
                        for (FaceInfo faceInfo : faceInfos) {
                            Log.i(TAG, "Detected face: " + faceInfo);
                        }
                        if (lang.equals("en")) {
                            handleFaceInfosEn(faceInfos);
                        } else {
                            handleFaceInfosVi(faceInfos);
                        }
                    }

                    @Override
                    public void onFailure(int i, @NonNull String s) {

                    }
                });
            }

            @Override
            public void onError() {
                faceApi.faceRecognize(10000, new ResponseListener<List<FaceInfo>>() {
                    @Override
                    public void onResponseSuccess(List<FaceInfo> faceInfos) {
                        for (FaceInfo faceInfo : faceInfos) {
                            Log.i(TAG, "Detected face: " + faceInfo);
                        }
                        if (lang.equals("en")) {
                            handleFaceInfosEn(faceInfos);
                        } else {
                            handleFaceInfosVi(faceInfos);
                        }
                    }

                    @Override
                    public void onFailure(int i, @NonNull String s) {

                    }
                });
            }
        });
    }

    public void handleRegister(String name) {
        currentUId = UUID.randomUUID().toString();
        faceApi.apiFaceRegister(currentUId, name, new ResponseListener<String>() {
            @Override
            public void onResponseSuccess(String s) {

            }

            @Override
            public void onFailure(int i, @NonNull String s) {

            }
        });
    }

    private String buildNamesSentence(List<String> names, String lang) {
        if (names.isEmpty())
            return "";
        if (names.size() == 1)
            return names.get(0);
        String last = names.get(names.size() - 1);
        String joiner = lang.equals("vi") ? " và " : " and ";
        return String.join(", ", names.subList(0, names.size() - 1)) + joiner + last;
    }

    public void handleFaceInfosEn(List<FaceInfo> faceInfos) {
        if (faceInfos == null || faceInfos.isEmpty()) {
            tts.doTTS("Are you there? Hello...", "en", null);
            return;
        }
        List<String> knownNames = new ArrayList<>();
        int strangerCount = 0;
        for (FaceInfo faceInfo : faceInfos) {
            if (faceInfo.getId() == null || faceInfo.getId().isEmpty()) {
                strangerCount++;
            } else {
                knownNames.add(faceInfo.getName());
            }
        }
        if (faceInfos.size() == 1 && strangerCount == 1) {
            tts.doTTS("I don't recognize you", "en", null);
        } else if (!knownNames.isEmpty()) {
            tts.doTTS("I see " + buildNamesSentence(knownNames, "en") + ".", "en", null);
        }
    }

    public void handleFaceInfosVi(List<FaceInfo> faceInfos) {
        if (faceInfos == null || faceInfos.isEmpty()) {
            tts.doTTS("Bạn có ở đó không?", "vi", null);
            return;
        }
        List<String> knownNames = new ArrayList<>();
        int strangerCount = 0;
        for (FaceInfo faceInfo : faceInfos) {
            if (faceInfo.getId() == null || faceInfo.getId().isEmpty()) {
                strangerCount++;
            } else {
                knownNames.add(faceInfo.getName());
            }
        }
        if (faceInfos.size() == 1 && strangerCount == 1) {
            tts.doTTS("Tôi không nhận ra bạn", "vi", null);
        } else if (!knownNames.isEmpty()) {
            tts.doTTS("Tôi thấy " + buildNamesSentence(knownNames, "vi") + ".", "vi", null);
        }
    }

    public void stopDetect() {
        faceApi.stopFindFace(new ResponseListener<Void>() {
            @Override
            public void onResponseSuccess(Void unused) {

            }

            @Override
            public void onFailure(int i, @NonNull String s) {

            }
        });
    }

    public void stopRegister() {
        faceApi.stopRegister(currentUId, new ResponseListener<Void>() {
            @Override
            public void onResponseSuccess(Void unused) {

            }

            @Override
            public void onFailure(int i, @NonNull String s) {

            }
        });
    }
}