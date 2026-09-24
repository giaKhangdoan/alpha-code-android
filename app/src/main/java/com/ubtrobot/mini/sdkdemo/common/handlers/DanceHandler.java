package com.ubtrobot.mini.sdkdemo.common.handlers;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import com.ubtrobot.action.ActionApi;
import com.ubtrobot.commons.Priority;
import com.ubtrobot.commons.ResponseListener;
import com.ubtrobot.express.ExpressApi;
import com.ubtrobot.express.listeners.AnimationListener;
import com.ubtrobot.lib.mouthledapi.MouthLedApi;
import com.ubtrobot.master.context.MasterContext;
import com.ubtrobot.mini.sdkdemo.common.CommandHandler;
import com.ubtrobot.mini.sdkdemo.log.LogLevel;
import com.ubtrobot.mini.sdkdemo.log.LogManager;
import com.ubtrobot.mini.sdkdemo.utils.RobotUtils;
import com.ubtrobot.mini.voice.MiniMediaPlayer;
import com.ubtrobot.mini.voice.protos.VoiceProto;

import java.util.ArrayList;
import java.util.List;

public class DanceHandler {
    private static final String TAG = "DanceHandler";
    private static DanceHandler instance;

    private MiniMediaPlayer miniPlayer;
    private ActionApi actionApi;
    private ExpressApi expressApi;
    private MouthLedApi mouthLedApi;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> scheduledActions = new ArrayList<>();
    private android.content.Context appContext;

    private DanceHandler() {
        // Private constructor for singleton
    }

    public static synchronized DanceHandler getInstance() {
        if (instance == null) {
            instance = new DanceHandler();
        }
        return instance;
    }

    public void init(android.content.Context context) {
        this.appContext = context.getApplicationContext();
        initRobot();
    }

    private void initRobot() {
        actionApi = ActionApi.get();
        expressApi = ExpressApi.get();
        mouthLedApi = MouthLedApi.get();
    }

    public void handleDanceWithMusic(JSONObject data) {
        if (data != null) {
            Log.i(TAG, "Handling dance with music command: " + data);
            jumpWithMusic(data);
        }
    }

    public void jumpWithMusic(JSONObject jsonObject) {
        MasterContext context = RobotUtils.getMasterContext();
        VoiceProto.Source source = RobotUtils.getVoiceProtoSource();

        initRobot();
        initPlayer(context, source, jsonObject);
    }

    private void initPlayer(MasterContext context, VoiceProto.Source source, JSONObject jsonObject) {
        Log.i(TAG, String.valueOf(jsonObject));
        try {
            String audioPath = jsonObject.getJSONObject("music_info").getString("music_file_url");
            miniPlayer = MiniMediaPlayer.create(context, source);
            miniPlayer.setDataSource(audioPath);

            miniPlayer.setOnPreparedListener(mp -> {
                Log.i(TAG, "Media ready, start playing");
                mp.start();
                // Run the script after the media is prepared
                // This ensures the media is ready before executing actions
                try {
                    playScriptFromJson(jsonObject);
                    Log.i(TAG, "Script execution done");
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing JSON", e);
                    LogManager.log(LogLevel.ERROR, TAG, "Error parsing JSON: " + e.getMessage());
                }
            });

            miniPlayer.setOnCompletionListener(mp -> Log.i(TAG, "Playback completed"));

            miniPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Error playing media: " + what + ", " + extra);
                LogManager.log(LogLevel.ERROR, TAG, "Error playing media: " + what + ", " + extra);
                return true;
            });

            miniPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing player", e);
            LogManager.log(LogLevel.ERROR, TAG, "Error initializing player: " + e.getMessage());
        }
    }

    private void playScriptFromJson(JSONObject script) {
        try {
            JSONArray actions = script.getJSONObject("activity").getJSONArray("actions");

            Log.i(TAG, "Playing script with " + actions.length() + " actions");
            LogManager.log(LogLevel.INFO, TAG, "Playing script with " + actions.length() + " actions");

            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                String actionId = action.getString("action_id");
                double startTime = action.getDouble("start_time");
                double duration = action.getDouble("duration");
                String type = action.getString("action_type");

                // Read color information from JSON
                JSONObject colorObj = action.optJSONObject("color");
                int a = 0, r = 255, g = 255, b = 255;
                if (colorObj != null) {
                    a = colorObj.optInt("a", 0);
                    r = colorObj.optInt("r", 255);
                    g = colorObj.optInt("g", 255);
                    b = colorObj.optInt("b", 255);
                }
                int finalA = a, finalR = r, finalG = g, finalB = b;
                Runnable runnable = () -> {
                    doAction(actionId, startTime, duration, type, finalA, finalR, finalG, finalB);
                };
                scheduledActions.add(runnable);
                handler.postDelayed(runnable, (long) (startTime * 1000));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in playScriptFromJson", e);
            LogManager.log(LogLevel.ERROR, TAG, "Error in playScriptFromJson: " + e.getMessage());
        }
    }

    public void stopAllScheduledActions() {
        for (Runnable r : scheduledActions) {
            handler.removeCallbacks(r);
        }
        scheduledActions.clear();
        if (miniPlayer != null) {
            miniPlayer.stop();
        }
    }

    /**
     * Alias for stopAllScheduledActions - called from RobotCommandHandler
     */
    public void stop() {
        stopAllScheduledActions();
        Log.i(TAG, "Dance stopped");
    }

    /**
     * Overloaded jumpWithMusic for Gson JsonArray support.
     * Converts Gson objects to org.json format and delegates.
     */
    public void jumpWithMusic(String musicUrl, com.google.gson.JsonArray actionsArray) {
        try {
            // Convert Gson to org.json
            JSONObject jsonObject = new JSONObject();
            JSONObject musicInfo = new JSONObject();
            musicInfo.put("music_file_url", musicUrl);
            jsonObject.put("music_info", musicInfo);

            JSONObject activity = new JSONObject();
            JSONArray actions = new JSONArray(actionsArray.toString());
            activity.put("actions", actions);
            jsonObject.put("activity", activity);

            // Call existing method
            jumpWithMusic(jsonObject);
        } catch (Exception e) {
            Log.e(TAG, "Error converting Gson to JSONObject", e);
        }
    }

    private void doAction(String actionId, double startTime, double duration, String type, int finalA, int finalR,
            int finalG, int finalB) {

        Log.i(TAG, "Executing action: " + actionId + " at time: " + startTime + " duration: " + duration);
        LogManager.log(LogLevel.INFO, TAG,
                "Executing action: " + actionId + " at time: " + startTime + " duration: " + duration);

        // Set LED color with activity duration time
        try {
            mouthLedApi.startNormalModel(Color.argb(finalA, finalR, finalG, finalB),
                    (int) (duration * 1000), Priority.NORMAL, null);
        } catch (Exception e) {
            Log.e(TAG, "Error setting LED color", e);
            LogManager.log(LogLevel.ERROR, TAG, "Error setting LED color: " + e.getMessage());
        }

        // Run action based on type
        switch (type) {
            case "dance":
                actionApi.playAction(actionId, new ResponseListener<Void>() {
                    @Override
                    public void onResponseSuccess(Void aVoid) {
                        Log.i(TAG, "Action " + actionId + " completed successfully");
                        LogManager.log(LogLevel.INFO, TAG, "Action " + actionId + " completed successfully");
                    }

                    @Override
                    public void onFailure(int errorCode, @NonNull String errorMessage) {
                        Log.e(TAG, "Action " + actionId + " failed: " + errorMessage);
                        LogManager.log(LogLevel.ERROR, TAG, "Action " + actionId + " failed: " + errorMessage);
                    }
                });
                break;
            case "expression":
                try {
                    Log.i(TAG, "Executing expression: " + actionId);
                    expressApi.doExpress(actionId, 1, true, Priority.HIGH, new AnimationListener() {
                        @Override
                        public void onAnimationStart() {
                            Log.i(TAG, "doExpress");
                            LogManager.log(LogLevel.INFO, TAG, "doExpress");
                        }

                        @Override
                        public void onAnimationEnd(int i) {
                            Log.i(TAG, "doExpress");
                            LogManager.log(LogLevel.INFO, TAG, "doExpress");
                        }

                        @Override
                        public void onAnimationRepeat(int loopNumber) {
                            Log.i(TAG, "doExpress" + loopNumber);
                            LogManager.log(LogLevel.INFO, TAG, "doExpress" + loopNumber);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error executing expression " + actionId, e);
                    LogManager.log(LogLevel.ERROR, TAG,
                            "Error executing expression " + actionId + ": " + e.getMessage());
                }
                break;
            default:
                Log.w(TAG, "Unknown action type: " + type + " for action: " + actionId);
                LogManager.log(LogLevel.WARN, TAG, "Unknown action type: " + type + " for action: " + actionId);
                break;
        }
    }

}
