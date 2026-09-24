package com.ubtrobot.mini.sdkdemo.server;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ubtrobot.action.ActionApi;
import com.ubtrobot.commons.Priority;
import com.ubtrobot.commons.ResponseListener;
import com.ubtrobot.express.ExpressApi;
import com.ubtrobot.express.listeners.AnimationListener;
import com.ubtrobot.masterevent.protos.SysMasterEvent;
import com.ubtrobot.mini.sysevent.EventApi;
import com.ubtrobot.mini.sysevent.SysEventApi;
import com.ubtrobot.motion.protos.Motion;
import com.ubtrobot.mini.sdkdemo.common.handlers.DanceHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.TTSHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler xử lý các lệnh điều khiển Robot.
 * Được gọi từ RobotHttpServer khi nhận request từ Flutter App.
 */
public class RobotCommandHandler {
    private static final String TAG = "RobotCommandHandler";

    private final Context context;
    private final Handler mainHandler;
    private final Gson gson;

    // Robot APIs
    private ActionApi actionApi;
    private ExpressApi expressApi;
    private EventApi eventApi;

    // Handlers
    private TTSHandler ttsHandler;
    private DanceHandler danceHandler;

    public RobotCommandHandler(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
        initializeApis();
    }

    private void initializeApis() {
        try {
            actionApi = ActionApi.get();
            expressApi = ExpressApi.get();
            eventApi = SysEventApi.get();

            ttsHandler = TTSHandler.getInstance();
            ttsHandler.init(context);

            danceHandler = DanceHandler.getInstance();
            danceHandler.init(context);

            Log.i(TAG, "Robot APIs initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Robot APIs", e);
        }
    }

    // ==================== STATUS ====================

    /**
     * Lấy trạng thái robot (pin, kết nối, ...)
     */
    public Map<String, Object> getRobotStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            // Battery level from EventApi
            if (eventApi != null) {
                SysMasterEvent.BatteryStatusData batteryData = eventApi.getCurrentBatteryInfoSync();
                if (batteryData != null) {
                    status.put("battery", batteryData.getLevel());
                    // BatteryStatusType: 0=discharging, 1=charging, 2=full
                    status.put("is_charging", batteryData.getStatus() == 1);
                    status.put("battery_status", batteryData.getStatus());
                } else {
                    status.put("battery", -1);
                    status.put("is_charging", false);
                }
            } else {
                status.put("battery", -1);
                status.put("is_charging", false);
            }

            // Action playing status
            if (actionApi != null) {
                status.put("is_playing", actionApi.isPlaying());
            } else {
                status.put("is_playing", false);
            }

            status.put("connected", true);
            status.put("timestamp", System.currentTimeMillis());

        } catch (Exception e) {
            Log.e(TAG, "Error getting robot status", e);
            status.put("error", e.getMessage());
        }

        return status;
    }

    // ==================== ACTIONS ====================

    /**
     * Lấy danh sách action hệ thống
     */
    public List<String> getActionList() {
        List<String> actions = new ArrayList<>();

        try {
            if (actionApi != null) {
                List<Motion.Action> actionInfos = actionApi.getActionList();
                if (actionInfos != null) {
                    for (Motion.Action action : actionInfos) {
                        actions.add(action.getId());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting action list", e);
        }

        return actions;
    }

    /**
     * Lấy danh sách action tùy chỉnh
     */
    public List<String> getCustomActionList() {
        List<String> actions = new ArrayList<>();

        try {
            if (actionApi != null) {
                List<Motion.Action> actionInfos = actionApi.getCustomizeActionList();
                if (actionInfos != null) {
                    for (Motion.Action action : actionInfos) {
                        actions.add(action.getId());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting custom action list", e);
        }

        return actions;
    }

    /**
     * Chạy một action
     */
    public void playAction(String actionId, boolean isCustom) {
        mainHandler.post(() -> {
            try {
                if (actionApi != null) {
                    ResponseListener<Void> callback = new ResponseListener<Void>() {
                        @Override
                        public void onResponseSuccess(Void aVoid) {
                            Log.d(TAG, "Action completed: " + actionId);
                        }

                        @Override
                        public void onFailure(int code, @NonNull String error) {
                            Log.e(TAG, "Action error: " + error);
                        }
                    };

                    if (isCustom) {
                        actionApi.playCustomizeAction(actionId, callback);
                    } else {
                        actionApi.playAction(actionId, callback);
                    }

                    Log.i(TAG, "Playing action: " + actionId + " (custom: " + isCustom + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error playing action", e);
            }
        });
    }

    /**
     * Dừng action đang chạy
     */
    public void stopAction() {
        mainHandler.post(() -> {
            try {
                if (actionApi != null) {
                    actionApi.stopAction();
                    Log.i(TAG, "Action stopped");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping action", e);
            }
        });
    }

    // ==================== TTS ====================

    /**
     * Text to Speech
     */
    public void speak(String text, String lang) {
        mainHandler.post(() -> {
            try {
                if (ttsHandler != null) {
                    ttsHandler.doTTS(text, lang, null);
                    Log.i(TAG, "Speaking: " + text + " (lang: " + lang + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in TTS", e);
            }
        });
    }

    /**
     * Dừng TTS
     */
    public void stopTTS() {
        mainHandler.post(() -> {
            try {
                if (ttsHandler != null) {
                    ttsHandler.stop();
                    Log.i(TAG, "TTS stopped");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping TTS", e);
            }
        });
    }

    // ==================== MOVEMENT ====================

    /**
     * Điều khiển di chuyển (joystick)
     * Note: Alpha Mini uses ActionApi with movement actions
     *
     * @param x         Trục X (-1 đến 1), trái/phải
     * @param y         Trục Y (-1 đến 1), trước/sau
     * @param headAngle Góc quay đầu (not used in this implementation)
     */
    public void move(double x, double y, double headAngle) {
        mainHandler.post(() -> {
            try {
                if (actionApi != null) {
                    int speed = (int) (Math.max(Math.abs(x), Math.abs(y)) * 100);

                    if (speed < 5) {
                        // Stop if joystick is centered
                        actionApi.stopAction();
                        return;
                    }

                    // Determine movement direction using action commands
                    String actionCode;
                    if (Math.abs(y) > Math.abs(x)) {
                        // Forward/backward movement
                        actionCode = y > 0 ? "forward" : "backward";
                    } else {
                        // Turn left/right
                        actionCode = x > 0 ? "turn_right" : "turn_left";
                    }

                    actionApi.playAction(actionCode, new ResponseListener<Void>() {
                        @Override
                        public void onResponseSuccess(Void aVoid) {
                            Log.d(TAG, "Move action completed: " + actionCode);
                        }

                        @Override
                        public void onFailure(int code, @NonNull String error) {
                            Log.e(TAG, "Move action error: " + error);
                        }
                    });

                    Log.d(TAG, "Move: x=" + x + ", y=" + y + ", action=" + actionCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in movement", e);
            }
        });
    }

    /**
     * Dừng di chuyển
     */
    public void stopMove() {
        mainHandler.post(() -> {
            try {
                if (actionApi != null) {
                    actionApi.stopAction();
                    Log.i(TAG, "Movement stopped");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping movement", e);
            }
        });
    }

    // ==================== DANCE ====================

    /**
     * Nhảy theo nhạc
     */
    public void danceWithMusic(JsonObject data) {
        mainHandler.post(() -> {
            try {
                if (danceHandler != null) {
                    // Parse music URL
                    String musicUrl = data.getAsJsonObject("music_info")
                            .get("music_file_url").getAsString();

                    // Parse actions
                    JsonArray actionsArray = data.getAsJsonObject("activity")
                            .getAsJsonArray("actions");

                    danceHandler.jumpWithMusic(musicUrl, actionsArray);
                    Log.i(TAG, "Dance with music started: " + musicUrl);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in dance with music", e);
            }
        });
    }

    /**
     * Dừng nhảy
     */
    public void stopDance() {
        mainHandler.post(() -> {
            try {
                if (danceHandler != null) {
                    danceHandler.stop();
                    Log.i(TAG, "Dance stopped");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping dance", e);
            }
        });
    }

    // ==================== EXPRESSION ====================

    /**
     * Hiển thị biểu cảm
     */
    public void showExpression(String expression) {
        mainHandler.post(() -> {
            try {
                if (expressApi != null) {
                    expressApi.doExpress(expression, -1, Priority.HIGH, new AnimationListener() {
                        @Override
                        public void onAnimationStart() {
                            Log.d(TAG, "Expression started: " + expression);
                        }

                        @Override
                        public void onAnimationEnd(int result) {
                            Log.d(TAG, "Expression completed: " + expression + ", result: " + result);
                        }

                        @Override
                        public void onAnimationRepeat(int count) {
                            Log.d(TAG, "Expression repeat: " + expression + ", count: " + count);
                        }
                    });
                    Log.i(TAG, "Showing expression: " + expression);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing expression", e);
            }
        });
    }

    // ==================== STOP ALL ====================

    /**
     * Dừng tất cả hoạt động
     */
    public void stopAll() {
        mainHandler.post(() -> {
            try {
                stopAction();
                stopTTS();
                stopMove();
                stopDance();
                if (expressApi != null) {
                    expressApi.stopExpress();
                }
                Log.i(TAG, "All activities stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping all", e);
            }
        });
    }
}
