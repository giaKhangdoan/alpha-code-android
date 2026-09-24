package com.ubtrobot.mini.sdkdemo.common;

import android.support.annotation.NonNull;
import android.util.Log;

import com.ubtech.utilcode.utils.Utils;
import com.ubtrobot.action.ActionApi;
import com.ubtrobot.action.ActionExApi;
import com.ubtrobot.commons.Priority;
import com.ubtrobot.commons.ResponseListener;
import com.ubtrobot.express.ExpressApi;
import com.ubtrobot.lib.mouthledapi.MouthLedApi;
import com.ubtrobot.mini.sdkdemo.common.handlers.ActionHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.CameraHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.CodingBlockHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.DanceHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.ExpressionHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.ExtendedActionHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.FaceHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.OsmoActionsHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.SkillHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.SmartHomeHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.SubmissionHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.SystemHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.TTSHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.WebRTCHandler;
import com.ubtrobot.mini.sdkdemo.custom.CameraPreviewCapture;
import com.ubtrobot.mini.sdkdemo.custom.tts.TTSCallback;
import com.ubtrobot.mini.sdkdemo.log.LogManager;
import com.ubtrobot.mini.sdkdemo.models.RobotRequestTypes;
import com.ubtrobot.mini.sdkdemo.models.response.OsmoCardAction;
import com.ubtrobot.mini.sdkdemo.socket.RobotMessageBuilder;
import com.ubtrobot.mini.sdkdemo.socket.RobotSocketManager;
import com.ubtrobot.sys.SysApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class CommandHandler {
    private static final String TAG = "CommandHandler";
    // Handler instances
    private ActionHandler actionHandler;
    private ExtendedActionHandler extendedActionHandler;
    private SkillHandler skillHandler;
    private ExpressionHandler expressionHandler;
    private DanceHandler danceHandler;
    private CameraHandler cameraHandler;
    private TTSHandler ttsHandler;
    private SystemHandler systemHandler;
    private FaceHandler faceHandler;
    private WebRTCHandler webRTCHandler;
    private OsmoActionsHandler osmoHandler;

    private CodingBlockHandler codingBlockHandler;

    private SubmissionHandler submissionHandler;

    private SmartHomeHandler smartHomeHandler;

    public CommandHandler() {
        // Initialize all handlers
        this.actionHandler = new ActionHandler();
        this.extendedActionHandler = new ExtendedActionHandler();
        this.skillHandler = new SkillHandler();
        this.expressionHandler = new ExpressionHandler();
        this.danceHandler = DanceHandler.getInstance();
        this.cameraHandler = new CameraHandler();
        this.ttsHandler = TTSHandler.getInstance();
        this.systemHandler = SystemHandler.get();
        this.faceHandler = FaceHandler.get();
        this.webRTCHandler = WebRTCHandler.getInstance();
        this.codingBlockHandler = new CodingBlockHandler();
        this.submissionHandler = new SubmissionHandler();
        this.smartHomeHandler = new SmartHomeHandler();
    }

    // Method to set socket manager for handlers that need it
    public void setSocketManager(com.ubtrobot.mini.sdkdemo.socket.RobotSocketManager socketManager) {
        this.systemHandler.setSocketManager(socketManager);
        this.webRTCHandler.setSocketManager(socketManager);
        this.osmoHandler = new OsmoActionsHandler();
    }

    public void handleCommand(String type, JSONObject data, String lang) throws JSONException {
        if (type == null || type.isEmpty()) {
            Log.e(TAG, "Invalid type");
            return;
        }

        String text = data.optString("text");
        String code = data.optString("code");
        Log.e(TAG, data.toString());

        try {
            switch (type) {
                case "video_capture":
                    cameraHandler.handleVideoGenerate(text, lang);
                    break;
                case "smart_home":
                    smartHomeHandler.smartHomeControl(data.optString("id"), data.optString("name"),
                            data.optString("message"), lang);
                    break;
                case "submission_start":
                    submissionHandler.handleSubmissionStart(data, lang);
                    break;

                case "submission_end":
                    submissionHandler.handleSubmissionEnd(data, lang);
                    break;

                case "get_system_info":
                    systemHandler.sendRobotStatus();
                    break;
                case "dance_with_music":
                    danceHandler.handleDanceWithMusic(data);
                    break;

                case "skill_helper":
                    skillHandler.handleSkillHelper(code);
                    break;

                case "action":
                    actionHandler.handleAction(code);
                    break;

                case "expression":
                    expressionHandler.handleExpression(code);
                    break;

                case "qr_code":
                    cameraHandler.handleQRCode(text, lang);
                    break;

                case "capture_osmo_card":
                    cameraHandler.handleOsmoCard(text, lang);
                    break;

                case "extended_action":
                    extendedActionHandler.handleExtendedAction(data);
                    break;

                case "object_detect_start":
                    ttsHandler.doTTS(text, lang, new TTSCallback() {
                        @Override
                        public void onStart() {
                        }

                        @Override
                        public void onDone() {
                            CameraPreviewCapture previewCapture = new CameraPreviewCapture(
                                    Utils.getContext().getApplicationContext());
                            previewCapture.openCamera(lang);
                        }

                        @Override
                        public void onError() {

                        }
                    });
                    break;
                case "face_recognize":
                    faceHandler.handleDetect(lang);
                    break;
                case "face_register":
                    String name = data.optString("name");
                    Log.i(TAG, name);
                    if (name != null && !name.isEmpty()) {
                        faceHandler.handleRegister(name);
                    } else {
                        ttsHandler.doTTS(lang.equals("en") ? "Please provide a name to register"
                                : "Vui lòng cung cấp tên để đăng ký", lang);
                    }
                    break;
                case "osmo_card":
                    JSONArray actionsArray = data.getJSONArray("actions");
                    List<OsmoCardAction> list = OsmoCardAction.parseActionsArray(actionsArray);
                    if (list.isEmpty()) {
                        Log.e(TAG, "No valid Osmo actions found");
                        ttsHandler.doTTS(
                                lang.equals("vi") ? "Không tìm thấy thẻ Osmo nào cả" : "I couldn't find any Osmo card",
                                lang);
                        return;
                    }
                    osmoHandler.executeActions(list, new OsmoActionsHandler.ExecutionCallback() {
                        @Override
                        public void onCompleted() {
                            Log.i(TAG, "Completed");
                            osmoHandler.stopMouthLed();
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, error);
                        }
                    });
                    break;
                case "webrtc_start":
                    webRTCHandler.handleWebRTCStart(Utils.getContext().getApplicationContext());
                    break;
                case "webrtc_stop":
                    webRTCHandler.handleWebRTCStop();
                    break;
                case "process_text": // This is for calling from Web. This isn't from within the app
                    byte[] msg = new RobotMessageBuilder().addParameter("text", code).setType("process-text").build();
                    RobotSocketManager.getInstance().sendBinaryMessage(msg);
                    break;
                case "coding_block":
                    JSONArray actions = data.optJSONArray("actions");
                    if (actions != null) {
                        codingBlockHandler.enqueueBlocks(actions, lang, new CodingBlockHandler.CodingBlockCallback() {
                            @Override
                            public void onBlockCodingStart() {

                            }

                            @Override
                            public void onBlockCodingEnd() {

                            }
                        });
                    }
                    break;
                case "coding_block_status":
                    byte[] message = new RobotMessageBuilder()
                            .addParameter("status", String.valueOf(codingBlockHandler.isExecuting()))
                            .setType(RobotRequestTypes.CODING_BLOCK_STATUS)
                            .build();
                    RobotSocketManager.getInstance().sendBinaryMessage(message);
                    break;
                case "stop_all_actions":
                    stopEverything();
                    break;
                case "shut_down":
                    SysApi.get().shutdown();
                    break;
                default:
                    ttsHandler.doTTS(text, lang);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling command: " + type + ", data: " + data, e);
        }
    }

    private void stopEverything() {
        ActionApi actionApi = ActionApi.get();
        ActionExApi actionExApi = ActionExApi.get();
        MouthLedApi ledApi = MouthLedApi.get();
        ExpressApi expressApi = ExpressApi.get();

        ttsHandler.stopIfPlaying();
        expressApi.stopExpress();
        danceHandler.stopAllScheduledActions();
        codingBlockHandler.clearQueue();
        faceHandler.stopDetect();
        faceHandler.stopRegister();
        ledApi.turnOff(Priority.HIGH, new ResponseListener() {
            @Override
            public void onResponseSuccess(Object o) {

            }

            @Override
            public void onFailure(int i, @NonNull String s) {

            }
        });
        if (actionApi.isPlaying()) {
            actionApi.stopAction();
            actionApi.playAction("stand_up", null);
        }

    }
}
