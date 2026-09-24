package com.ubtrobot.mini.sdkdemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;

import com.ubtech.utilcode.utils.Utils;
import com.ubtrobot.mini.sdkdemo.activity.RobotWebRTCActivity;
import com.ubtrobot.commons.ResponseListener;
import com.ubtrobot.mini.sdkdemo.common.handlers.TTSHandler;
import com.ubtrobot.mini.sdkdemo.log.LogLevel;
import com.ubtrobot.mini.sdkdemo.log.LogManager;
import com.ubtrobot.mini.sdkdemo.socket.AutoStartManager;
import com.ubtrobot.mini.sdkdemo.socket.RobotSocketClient;
import com.ubtrobot.mini.sdkdemo.speech.DemoSpeechJava;
import com.ubtrobot.mini.sdkdemo.uiActivities.ActionApiActivity;
import com.ubtrobot.mini.sdkdemo.uiActivities.FaceApiActivity;
import com.ubtrobot.mini.sdkdemo.uiActivities.FaceApiCRUD;
import com.ubtrobot.mini.sdkdemo.uiActivities.MicrophoneActivity;
import com.ubtrobot.mini.sdkdemo.uiActivities.SpeechApiActivity;
import com.ubtrobot.mini.sdkdemo.uiActivities.SysEventTestActivity;
import com.ubtrobot.mini.sdkdemo.uiActivities.TakePicApiActivity;
import com.ubtrobot.sys.SysApi;
import com.ubtrobot.mini.sdkdemo.server.RobotHttpServer;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by lulin.wu on 2018/6/19.
 */

public class MainActivity extends Activity {
    public static final String TAG = "DEBUG";
    private RobotHttpServer httpServer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        Context appContext = Utils.getContext().getApplicationContext();
        TTSHandler.getInstance().init(appContext);
        AutoStartManager.startWebSocketService(appContext);

        // Start HTTP Server for Flutter App communication
        startHttpServer(appContext);

        // Test log to verify remote logging is working
        LogManager.log(LogLevel.INFO, "MainActivity", "App started successfully", "app_lifecycle", "app_start");

        checkWriteSettingsPermission(this);
        Button forceConnect = (Button) findViewById(R.id.force_connect);
        Button forceWake = (Button) findViewById(R.id.force_wakeup);
        forceConnect.setOnClickListener(l -> {
            RobotSocketClient.getInstance().forceConnect();
        });
        forceWake.setOnClickListener(v -> {
            DemoSpeechJava.getInstance().wakeUp();
            SysApi.get().startup(new ResponseListener<Void>() {
                @Override
                public void onResponseSuccess(Void unused) {

                }

                @Override
                public void onFailure(int i, @NonNull String s) {

                }
            });
        });
    }

    public void micTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, MicrophoneActivity.class);
        startActivity(intent);

    }

    public void musicApiTest(View view) {
        // TODO: implement this method
    }

    public void robotWebRTCActivity(View view) {
        Intent intent = new Intent();
        intent.setClass(this, RobotWebRTCActivity.class);
        startActivity(intent);
    }

    public void speechApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, SpeechApiActivity.class);
        startActivity(intent);
    }

    public void actionApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, ActionApiActivity.class);
        startActivity(intent);
    }

    public void sysEventTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, SysEventTestActivity.class);
        startActivity(intent);
    }

    public void takePicApiTest(View view) {
        Intent intent = new Intent();
        intent.setClass(this, TakePicApiActivity.class);
        startActivity(intent);
    }

    public void faceApiTest(View view) {
        Intent intent = new Intent(this, FaceApiActivity.class);
        startActivity(intent);
    }

    public void faceApiCRUD(View view) {
        Intent intent = new Intent(this, FaceApiCRUD.class);
        startActivity(intent);
    }

    public void testVietnameseTTS(View view) {
        com.ubtrobot.mini.sdkdemo.custom.tts.VietnameseTTS tts = com.ubtrobot.mini.sdkdemo.custom.tts.VietnameseTTS
                .getInstance();
        String debugInfo = tts.debugTTSInfo();

        // Hiển thị thông tin debug lên Dialog
        new android.app.AlertDialog.Builder(this)
                .setTitle("Kiểm tra TTS")
                .setMessage(debugInfo)
                .setPositiveButton("Phát thử", (dialog, which) -> {
                    String hi = "Xin chào, tôi là Alpha Mini. Tôi đang nói tiếng Việt.";
                    tts.doTTS(hi);
                })
                .setNegativeButton("Mở Cài Đặt", (dialog, which) -> {
                    try {
                        Intent intent = new Intent();
                        intent.setAction("com.android.settings.TTS_SETTINGS");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        android.widget.Toast.makeText(this, "Không mở được cài đặt: " + e.getMessage(),
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void checkWriteSettingsPermission(Context context) {
        if (!Settings.System.canWrite(context)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        }
    }

    /**
     * Start HTTP Server for Flutter App communication
     */
    private void startHttpServer(Context context) {
        try {
            httpServer = new RobotHttpServer(context);
            httpServer.startServer();

            // Log IP address for debugging
            String ipAddress = getLocalIpAddress();
            Log.i(TAG, "HTTP Server started on http://" + ipAddress + ":8080");
            LogManager.log(LogLevel.INFO, TAG, "HTTP Server started on http://" + ipAddress + ":8080", "server",
                    "http_server_start");

            // Show IP address toast
            android.widget.Toast.makeText(this,
                    "HTTP Server: http://" + ipAddress + ":8080",
                    android.widget.Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start HTTP Server", e);
            LogManager.log(LogLevel.ERROR, TAG, "Failed to start HTTP Server: " + e.getMessage(), "server",
                    "http_server_error");
        }
    }

    /**
     * Get local IP address of the device
     */
    private String getLocalIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                int ipInt = wifiManager.getConnectionInfo().getIpAddress();
                byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array();
                return InetAddress.getByAddress(bytes).getHostAddress();
            }
        } catch (UnknownHostException e) {
            Log.e(TAG, "Failed to get IP address", e);
        }
        return "Unknown";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop HTTP server when activity is destroyed
        if (httpServer != null) {
            httpServer.stopServer();
            Log.i(TAG, "HTTP Server stopped");
        }
    }
}
