package com.alphacode.wifiprobe;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Standalone, deliberately small probe for Alpha Mini Android 7/API 24.
 * It has an isolated optional boot receiver and does not reference the production Alpha Code APK.
 */
public class MainActivity extends Activity {
    private static final String TAG = "AlphaMiniWifiProbe";
    private static final int REQUEST_WIFI_PERMISSIONS = 1001;
    private static final int HEALTH_PORT = 8787;
    private static final long GROUP_INFO_RETRY_MS = 500L;
    private static final long NETWORK_POLL_MS = 3000L;
    private static final long NETWORK_TIMEOUT_MS = 60000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newCachedThreadPool();

    private WifiManager wifiManager;
    private WifiP2pManager p2pManager;
    private WifiP2pManager.Channel p2pChannel;
    private BroadcastReceiver receiver;
    private boolean receiverRegistered;
    private ServerSocket healthServer;
    private Thread healthThread;
    private OkHttpClient backendClient;

    private TextView statusText;
    private TextView setupText;
    private TextView scanText;
    private TextView logText;
    private EditText ssidInput;
    private EditText passwordInput;
    private EditText serialInput;
    private EditText modelIdInput;
    private EditText websocketBaseInput;

    private int previousNetworkId = -1;
    private int addedNetworkId = -1;
    private boolean wifiSnapshotTaken;
    private long networkAttemptStartedAt;
    private int groupInfoAttempts;
    private String currentGroupIp = "";
    private String currentGroupSsid = "";
    private String currentGroupPassphrase = "";
    private long lastScanAt;
    private String lastPortalAction = "idle";
    private String lastBackendResult = "not_checked";
    private boolean autoStartScheduled;
    private volatile boolean setupGroupActive;
    private final Object scanLock = new Object();
    private boolean scanPending;
    private long scanRequestAt;
    private String lastScanJson = "{\"ok\":true,\"scanStarted\":false,\"timestamp\":0,\"source\":\"saved\",\"networks\":[]}";
    private volatile boolean portalScanPending;
    private volatile long portalScanStartedAt;
    private volatile String portalScanResult = "{\"ok\":true,\"pending\":false,\"source\":\"saved\",\"networks\":[]}";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_probe);

        bindViews();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        p2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (p2pManager != null) {
            p2pChannel = p2pManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
                @Override
                public void onChannelDisconnected() {
                    appendLog("P2P channel disconnected; mở lại app nếu cần.");
                }
            });
        }

        serialInput.setText(defaultSerial());
        modelIdInput.setText(BuildConfig.DEFAULT_MODEL_ID);
        websocketBaseInput.setText(BuildConfig.DEFAULT_WEBSOCKET_BASE);
        installReceiver();
        wireButtons();

        if (hasLocationPermission()) {
            setStatus("Sẵn sàng. Bấm Tạo P2P khi cần cấu hình.");
            scheduleAutoStart();
        } else {
            setStatus("Cần quyền vị trí để quét Wi-Fi/P2P trên Android 7.");
            requestWifiPermissions();
        }
    }

    private void bindViews() {
        statusText = findViewById(R.id.status_text);
        setupText = findViewById(R.id.setup_text);
        scanText = findViewById(R.id.scan_text);
        logText = findViewById(R.id.log_text);
        ssidInput = findViewById(R.id.ssid_input);
        passwordInput = findViewById(R.id.password_input);
        serialInput = findViewById(R.id.serial_input);
        modelIdInput = findViewById(R.id.model_id_input);
        websocketBaseInput = findViewById(R.id.websocket_base_input);
    }

    private void wireButtons() {
        Button createButton = findViewById(R.id.create_group_button);
        Button stopButton = findViewById(R.id.stop_button);
        Button scanButton = findViewById(R.id.scan_button);
        Button connectButton = findViewById(R.id.connect_button);
        Button backendButton = findViewById(R.id.backend_button);

        createButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createSetupGroup();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopAndRestore();
            }
        });
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanWifiNetworks();
            }
        });
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectToTargetWifi();
            }
        });
        backendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkBackendWebSocket();
            }
        });
    }

    private void installReceiver() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                    showScanResultsFromBroadcast();
                } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                    NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (info != null && info.isConnected()) {
                        appendLog("Wi-Fi broadcast: connected.");
                    }
                } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                    int error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1);
                    if (error == WifiManager.ERROR_AUTHENTICATING) {
                        setStatus("Wi-Fi xác thực thất bại; kiểm tra SSID/mật khẩu và thử lại.");
                        appendLog("supplicant authentication failure");
                    }
                } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                    requestP2pInfo();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        registerReceiver(receiver, filter);
        receiverRegistered = true;
    }

    private void requestWifiPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_WIFI_PERMISSIONS);
        }
    }

    private boolean hasLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WIFI_PERMISSIONS && hasLocationPermission()) {
            setStatus("Đã cấp quyền. Sẵn sàng tạo P2P/scan.");
            scheduleAutoStart();
        } else if (requestCode == REQUEST_WIFI_PERMISSIONS) {
            setStatus("Thiếu quyền vị trí; có thể không quét được Wi-Fi.");
        }
    }

    private void scheduleAutoStart() {
        if (autoStartScheduled) {
            return;
        }
        autoStartScheduled = true;
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                lastPortalAction = "pre_scan_before_p2p";
                prepareAutoStart();
            }
        }, 800L);
    }

    /**
     * This MT6755/API-24 ROM can complete a driver scan while P2P is active but
     * return an empty app-level ScanResult cache. Scan once in station mode before
     * creating the setup group, then expose that cache to the portal.
     */
    private void prepareAutoStart() {
        snapshotCurrentWifi();
        setupGroupActive = false;
        stopHealthServer();
        if (p2pManager != null && p2pChannel != null) {
            p2pManager.removeGroup(p2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    schedulePreP2pScan();
                }

                @Override
                public void onFailure(int reason) {
                    schedulePreP2pScan();
                }
            });
        } else {
            schedulePreP2pScan();
        }
    }

    private void schedulePreP2pScan() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startPreP2pScan();
            }
        }, 2000L);
    }

    private void startPreP2pScan() {
        try {
            boolean started = wifiManager != null && wifiManager.startScan();
            appendLog("pre-P2P startScan=" + started);
        } catch (SecurityException error) {
            appendLog("pre-P2P scan permission denied; continuing to P2P.");
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String result = readCurrentScanJson(false);
                appendLog("pre-P2P scan cache updated; bytes=" + result.length());
                lastPortalAction = "auto_create_group";
                createSetupGroup();
            }
        }, 30000L);
    }

    private void createSetupGroup() {
        if (!hasLocationPermission()) {
            requestWifiPermissions();
            return;
        }
        if (p2pManager == null || p2pChannel == null) {
            setStatus("ROM không cung cấp WifiP2pManager/channel.");
            return;
        }

        snapshotCurrentWifi();
        stopHealthServer();
        setStatus("Đang tạo P2P group...");
        appendLog("createGroup bắt đầu; không dùng SoftAP reflection.");

        p2pManager.removeGroup(p2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                createP2pGroupNow();
            }

            @Override
            public void onFailure(int reason) {
                createP2pGroupNow();
            }
        });
    }

    private void createP2pGroupNow() {
        p2pManager.createGroup(p2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                setStatus("P2P createGroup thành công; đang lấy SSID/passphrase/IP...");
                groupInfoAttempts = 0;
                requestP2pInfoWithRetry();
            }

            @Override
            public void onFailure(int reason) {
                setStatus("P2P createGroup thất bại: " + p2pFailure(reason));
                appendLog("createGroup failure=" + p2pFailure(reason));
            }
        });
    }

    private void requestP2pInfoWithRetry() {
        groupInfoAttempts++;
        requestP2pInfo();
        if (groupInfoAttempts < 20) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    requestP2pInfoWithRetry();
                }
            }, GROUP_INFO_RETRY_MS);
        }
    }

    private void requestP2pInfo() {
        if (p2pManager == null || p2pChannel == null) {
            return;
        }
        p2pManager.requestGroupInfo(p2pChannel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                if (group == null) {
                    return;
                }
                final String networkName = group.getNetworkName();
                final String passphrase = group.getPassphrase();
                currentGroupSsid = networkName == null ? "" : networkName;
                currentGroupPassphrase = passphrase == null ? "" : passphrase;
                p2pManager.requestConnectionInfo(p2pChannel, new WifiP2pManager.ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                        String ip = "";
                        if (info != null && info.groupOwnerAddress != null) {
                            ip = info.groupOwnerAddress.getHostAddress();
                        }
                        if (TextUtils.isEmpty(ip)) {
                            ip = findP2pIpv4();
                        }
                        currentGroupIp = ip == null ? "" : ip;
                        setupText.setText("SSID: " + safe(networkName)
                                + "\nPassphrase: " + safe(passphrase)
                                + "\nGroup owner: " + (info != null && info.isGroupOwner)
                                + "\nInterface: " + safe(findP2pInterfaceName())
                                + "\nGroup-owner IP: " + safe(currentGroupIp)
                                + "\nPortal: http://" + (TextUtils.isEmpty(currentGroupIp) ? "<IP>" : currentGroupIp) + ":" + HEALTH_PORT + "/health");
                        if (!TextUtils.isEmpty(networkName)) {
                            setupGroupActive = true;
                            startHealthServer();
                            setStatus("P2P setup sẵn sàng; thử GET /health từ điện thoại/laptop.");
                        }
                    }
                });
            }
        });
    }

    private void startHealthServer() {
        if (healthThread != null) {
            return;
        }
        healthThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Bind all interfaces so an ADB port-forward can bootstrap a headless robot.
                    // Keep the server alive during a station-network switch so the USB portal
                    // can configure another SSID without restarting the APK.
                    healthServer = new ServerSocket(HEALTH_PORT);
                    appendLog("HTTP health server listening on port " + HEALTH_PORT);
                    while (!Thread.currentThread().isInterrupted() && !healthServer.isClosed()) {
                        Socket socket = healthServer.accept();
                        handlePortalRequest(socket);
                    }
                } catch (IOException error) {
                    if (!Thread.currentThread().isInterrupted()) {
                        appendLog("HTTP server stopped: " + error.getClass().getSimpleName());
                    }
                }
            }
        }, "alpha-mini-health");
        healthThread.start();
    }

    private void handlePortalRequest(Socket socket) {
        try {
            socket.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && line.length() > 0) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                    } catch (NumberFormatException ignored) {
                        contentLength = 0;
                    }
                }
            }
            char[] bodyChars = new char[Math.max(0, Math.min(contentLength, 8192))];
            int bodyRead = 0;
            while (bodyRead < bodyChars.length) {
                int count = reader.read(bodyChars, bodyRead, bodyChars.length - bodyRead);
                if (count < 0) {
                    break;
                }
                bodyRead += count;
            }
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                writeHttpResponse(socket, "400 Bad Request", "application/json", "{\"ok\":false,\"error\":\"bad_request\"}");
                return;
            }
            String method = requestParts[0];
            String target = requestParts[1];
            String path = target;
            Map<String, String> query = new LinkedHashMap<>();
            int queryIndex = target.indexOf('?');
            if (queryIndex >= 0) {
                path = target.substring(0, queryIndex);
                query = parseForm(target.substring(queryIndex + 1));
            }
            String requestBody = new String(bodyChars, 0, bodyRead);
            Map<String, String> form = parseForm(requestBody);

            if ("GET".equals(method) && "/".equals(path)) {
                writeHttpResponse(socket, "200 OK", "text/html; charset=utf-8", portalHtml());
            } else if ("GET".equals(method) && "/health".equals(path)) {
                writeHttpResponse(socket, "200 OK", "application/json", "{\"ok\":true,\"service\":\"alpha-mini-wifi-probe\"}");
            } else if ("GET".equals(method) && "/api/status".equals(path)) {
                writeHttpResponse(socket, "200 OK", "application/json", statusJson());
            } else if ("GET".equals(method) && "/api/scan".equals(path)) {
                boolean forceScan = "1".equals(query.get("force")) || "true".equalsIgnoreCase(query.get("force"));
                if (isSetupPortalActive() && hasScanCache() && !forceScan) {
                    synchronized (scanLock) {
                        writeHttpResponse(socket, "200 OK", "application/json", lastScanJson);
                    }
                } else if (isSetupPortalActive()) {
                    startPortalScan();
                    writeHttpResponse(socket, "202 Accepted", "application/json", "{\"ok\":true,\"pending\":true,\"message\":\"P2P will pause while the radio scans\"}");
                } else {
                    writeHttpResponse(socket, "200 OK", "application/json", scanJson());
                }
            } else if ("GET".equals(method) && "/api/scan/status".equals(path)) {
                writeHttpResponse(socket, "200 OK", "application/json", portalScanStatusJson());
            } else if ("POST".equals(method) && "/api/connect".equals(path)) {
                final String ssid = form.get("ssid");
                final String password = form.get("password");
                if (TextUtils.isEmpty(ssid)) {
                    writeHttpResponse(socket, "400 Bad Request", "application/json", "{\"ok\":false,\"error\":\"ssid_required\"}");
                } else {
                    lastPortalAction = "connect_requested";
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            connectToTargetWifi(ssid, password == null ? "" : password);
                        }
                    });
                    writeHttpResponse(socket, "202 Accepted", "application/json", "{\"ok\":true,\"accepted\":true}");
                }
            } else if ("POST".equals(method) && "/api/backend".equals(path)) {
                final String serial = form.get("serial");
                final String modelId = form.get("modelId");
                final String base = form.get("base");
                lastPortalAction = "backend_requested";
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        checkBackendWebSocket(serial, modelId, base);
                    }
                });
                writeHttpResponse(socket, "202 Accepted", "application/json", "{\"ok\":true,\"accepted\":true}");
            } else if ("POST".equals(method) && "/api/stop".equals(path)) {
                lastPortalAction = "stop_requested";
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        stopAndRestore();
                    }
                });
                writeHttpResponse(socket, "202 Accepted", "application/json", "{\"ok\":true,\"accepted\":true}");
            } else {
                writeHttpResponse(socket, "404 Not Found", "application/json", "{\"ok\":false,\"error\":\"not_found\"}");
            }
        } catch (SocketTimeoutException ignored) {
            // A client that does not complete an HTTP request is not a probe failure.
        } catch (IOException error) {
            appendLog("HTTP client error: " + error.getClass().getSimpleName());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void writeHttpResponse(Socket socket, String status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        OutputStream output = socket.getOutputStream();
        output.write(("HTTP/1.1 " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes("UTF-8"));
        output.write(bytes);
        output.flush();
    }

    private Map<String, String> parseForm(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        if (TextUtils.isEmpty(body)) {
            return values;
        }
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            try {
                values.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
            } catch (Exception ignored) {
                values.put(key, value);
            }
        }
        return values;
    }

    private String statusJson() {
        String stationSsid = "";
        String stationIp = "";
        try {
            WifiInfo info = wifiManager == null ? null : wifiManager.getConnectionInfo();
            if (info != null) {
                stationSsid = info.getSSID();
                stationIp = info.getIpAddress() == 0 ? "" : formatIpv4(info.getIpAddress());
            }
        } catch (SecurityException ignored) {
        }
        return "{\"ok\":true"
                + ",\"action\":\"" + jsonEscape(lastPortalAction) + "\""
                + ",\"serial\":\"" + jsonEscape(defaultSerial()) + "\""
                + ",\"modelId\":\"" + jsonEscape(BuildConfig.DEFAULT_MODEL_ID) + "\""
                + ",\"groupSsid\":\"" + jsonEscape(currentGroupSsid) + "\""
                + ",\"groupPassphrase\":\"" + jsonEscape(currentGroupPassphrase) + "\""
                + ",\"groupOwnerIp\":\"" + jsonEscape(currentGroupIp) + "\""
                + ",\"portal\":\"http://" + jsonEscape(currentGroupIp) + ":" + HEALTH_PORT + "\""
                + ",\"stationSsid\":\"" + jsonEscape(stationSsid) + "\""
                + ",\"stationIp\":\"" + jsonEscape(stationIp) + "\""
                + ",\"backend\":\"" + jsonEscape(lastBackendResult) + "\""
                + ",\"lastScanAt\":" + lastScanAt
                + "}";
    }

    private String portalScanStatusJson() {
        if (portalScanPending) {
            return "{\"ok\":true,\"pending\":true,\"elapsedMs\":"
                    + (System.currentTimeMillis() - portalScanStartedAt) + "}";
        }
        return portalScanResult;
    }

    private void startPortalScan() {
        if (portalScanPending) {
            return;
        }
        portalScanPending = true;
        portalScanStartedAt = System.currentTimeMillis();
        lastPortalAction = "portal_scan_pause_p2p";
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                setupGroupActive = false;
                if (p2pManager != null && p2pChannel != null) {
                    p2pManager.removeGroup(p2pChannel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            schedulePortalStationScan();
                        }

                        @Override
                        public void onFailure(int reason) {
                            schedulePortalStationScan();
                        }
                    });
                } else {
                    schedulePortalStationScan();
                }
            }
        });
    }

    private void schedulePortalStationScan() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                portalScanStartedAt = System.currentTimeMillis();
                try {
                    boolean started = wifiManager != null && wifiManager.startScan();
                    appendLog("portal station startScan=" + started);
                } catch (SecurityException error) {
                    portalScanResult = "{\"ok\":false,\"pending\":false,\"error\":\"permission_denied\",\"networks\":[]}";
                    portalScanPending = false;
                    recreateSetupGroupAfterPortalScan();
                    return;
                }
                pollPortalStationScan();
            }
        }, 1500L);
    }

    private void pollPortalStationScan() {
        String result = readCurrentScanJson(true);
        boolean hasFreshScan = result.contains("\"source\":\"scan\"")
                && System.currentTimeMillis() - portalScanStartedAt >= 5000L;
        boolean timedOut = System.currentTimeMillis() - portalScanStartedAt >= 30000L;
        if (hasFreshScan || timedOut) {
            portalScanResult = result;
            portalScanPending = false;
            lastPortalAction = hasFreshScan ? "portal_scan_ready" : "portal_scan_empty";
            recreateSetupGroupAfterPortalScan();
            return;
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                pollPortalStationScan();
            }
        }, 1000L);
    }

    private void recreateSetupGroupAfterPortalScan() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                lastPortalAction = "recreate_group_after_scan";
                createSetupGroup();
            }
        }, 500L);
    }

    private String scanJson() {
        if (wifiManager == null) {
            return "{\"ok\":false,\"error\":\"wifi_unavailable\"}";
        }
        if (isSetupPortalActive()) {
            synchronized (scanLock) {
                if (!lastScanJson.contains("\"networks\":[]")) {
                    return lastScanJson;
                }
            }
        }
        long requestAt = System.currentTimeMillis();
        synchronized (scanLock) {
            scanRequestAt = requestAt;
            scanPending = true;
        }
        boolean scanStarted = false;
        try {
            scanStarted = wifiManager.startScan();
        } catch (SecurityException error) {
            synchronized (scanLock) {
                scanPending = false;
                scanLock.notifyAll();
            }
            return "{\"ok\":false,\"error\":\"permission_denied\"}";
        }

        if (!scanStarted) {
            synchronized (scanLock) {
                scanPending = false;
                scanLock.notifyAll();
            }
        } else {
            synchronized (scanLock) {
                // Alpha Mini's MT6755 scan can take 20-25 seconds before the
                // framework cache is populated, even though startScan() returns.
                long deadline = System.currentTimeMillis() + 25000L;
                while (scanPending && System.currentTimeMillis() < deadline) {
                    try {
                        scanLock.wait(250L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                scanPending = false;
            }
        }
        // Some API-24 ROMs complete the driver scan but do not deliver the
        // SCAN_RESULTS_AVAILABLE_ACTION to an ordinary foreground receiver while
        // P2P is active. Read the cache directly after the wait as a fallback.
        String current = readCurrentScanJson(scanStarted);
        // Do not stop at the early broadcast: on this ROM the framework event can
        // precede the actual getScanResults() cache by more than 15 seconds.
        long cacheDeadline = requestAt + 30000L;
        while (current.contains("\"networks\":[]") && System.currentTimeMillis() < cacheDeadline) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            current = readCurrentScanJson(scanStarted);
        }
        if (!current.contains("\"networks\":[]")) {
            return current;
        }
        synchronized (scanLock) {
            return lastScanJson.replaceFirst("\\\"scanStarted\\\":(true|false)", "\\\"scanStarted\\\":" + scanStarted);
        }
    }

    private boolean isSetupPortalActive() {
        return setupGroupActive || healthThread != null;
    }

    private boolean hasScanCache() {
        synchronized (scanLock) {
            return !lastScanJson.contains("\"networks\":[]");
        }
    }

    private String readCurrentScanJson(boolean scanStarted) {
        try {
            List<ScanResult> results = wifiManager.getScanResults();
            Map<String, ScanResult> uniqueResults = new LinkedHashMap<>();
            if (results != null) {
                for (ScanResult result : results) {
                    String key = TextUtils.isEmpty(result.SSID) ? "<hidden>" : result.SSID;
                    ScanResult existing = uniqueResults.get(key);
                    if (existing == null || result.level > existing.level) {
                        uniqueResults.put(key, result);
                    }
                }
            }
            List<String> savedSsids = uniqueResults.isEmpty() ? savedWifiSsids() : new ArrayList<String>();
            String source = uniqueResults.isEmpty() ? "saved" : "scan";
            long timestamp = System.currentTimeMillis();
            StringBuilder json = new StringBuilder("{\"ok\":true,\"scanStarted\":");
            json.append(scanStarted).append(",\"timestamp\":").append(timestamp)
                    .append(",\"source\":\"").append(source).append("\",\"networks\":[");
            boolean first = true;
            for (Map.Entry<String, ScanResult> entry : uniqueResults.entrySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                ScanResult result = entry.getValue();
                json.append("{\"ssid\":\"").append(jsonEscape(entry.getKey()))
                        .append("\",\"security\":\"").append(jsonEscape(security(result.capabilities)))
                        .append("\",\"level\":").append(result.level).append('}');
            }
            for (String savedSsid : savedSsids) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{\"ssid\":\"").append(jsonEscape(savedSsid))
                        .append("\",\"security\":\"SAVED\",\"level\":-127}");
            }
            json.append("]}");
            boolean hasNetworks = !uniqueResults.isEmpty() || !savedSsids.isEmpty();
            synchronized (scanLock) {
                if (hasNetworks) {
                    lastScanAt = timestamp;
                    lastScanJson = json.toString();
                }
            }
            return json.toString();
        } catch (SecurityException error) {
            return "{\"ok\":false,\"error\":\"permission_denied\"}";
        }
    }

    private List<String> savedWifiSsids() {
        List<String> saved = new ArrayList<>();
        if (wifiManager == null) {
            return saved;
        }
        try {
            List<WifiConfiguration> configured = wifiManager.getConfiguredNetworks();
            if (configured != null) {
                for (WifiConfiguration item : configured) {
                    if (item.SSID == null || item.SSID.length() == 0) {
                        continue;
                    }
                    String ssid = item.SSID;
                    if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() >= 2) {
                        ssid = ssid.substring(1, ssid.length() - 1);
                    }
                    if (!saved.contains(ssid)) {
                        saved.add(ssid);
                    }
                }
            }
        } catch (SecurityException ignored) {
        }
        return saved;
    }

    private String portalHtml() {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Alpha Mini Wi-Fi</title>"
                + "<style>body{font-family:Arial,sans-serif;max-width:720px;margin:24px auto;padding:0 16px;color:#17202a}button,input,select{font-size:16px;padding:10px;margin:5px 0;width:100%;box-sizing:border-box}button{background:#1565c0;color:white;border:0;border-radius:4px}.danger{background:#a32020}pre{white-space:pre-wrap;background:#f1f3f4;padding:12px;border-radius:5px}small{color:#566}</style></head><body>"
                + "<h1>Alpha Mini Wi-Fi Setup</h1><p>Chỉ dùng khi cần đổi mạng. Mật khẩu mạng nhà không được ghi vào URL hoặc log.</p>"
                + "<h2>P2P setup</h2><pre id=\"status\">Đang tải...</pre>"
                + "<h2>Wi-Fi đích</h2><button onclick=\"scan()\">Quét Wi-Fi</button><select id=\"ssid\"><option value=\"\">Chọn SSID hoặc nhập bên dưới</option></select>"
                + "<input id=\"manual\" placeholder=\"SSID ẩn / nhập thủ công\"><input id=\"password\" type=\"password\" placeholder=\"Mật khẩu Wi-Fi\"><button onclick=\"connect()\">Kết nối Wi-Fi</button>"
                + "<h2>Backend</h2><input id=\"serial\" placeholder=\"Robot serial\"><input id=\"model\" placeholder=\"x-robot-model-id\"><input id=\"base\" value=\"wss://backend.alpa.vn/ws\"><button onclick=\"backend()\">Kiểm tra backend</button>"
                + "<button class=\"danger\" onclick=\"stop()\">Dừng / khôi phục</button><p><small>Portal tạm thời chạy ở port 8787. Nếu không thấy Wi-Fi setup, dùng ADB forward để bootstrap.</small></p>"
                + "<script>const $=id=>document.getElementById(id);const wait=ms=>new Promise(r=>setTimeout(r,ms));async function get(u){return (await fetch(u,{cache:'no-store'})).json()}async function post(u,d){return (await fetch(u,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(d)})).json()}async function status(){try{let d=await get('/api/status');if(!$('serial').value&&d.serial)$('serial').value=d.serial;if(!$('model').value&&d.modelId)$('model').value=d.modelId;$('status').textContent='Setup SSID: '+d.groupSsid+'\\nPassphrase: '+d.groupPassphrase+'\\nPortal: '+d.portal+'\\nStation: '+(d.stationSsid||'-')+' '+(d.stationIp||'')+'\\nAction: '+d.action+'\\nBackend: '+d.backend}catch(e){$('status').textContent='Mất kết nối portal'}}async function scan(){try{$('status').textContent='Đang tạm dừng P2P và quét Wi-Fi; có thể mất tối đa 30 giây';let d=await get('/api/scan?force=1');for(let i=0;d.pending&&i<40;i++){await wait(1000);try{d=await get('/api/scan/status')}catch(e){await wait(1000)}}let s=$('ssid');s.innerHTML='<option value=\"\">Chọn SSID hoặc nhập bên dưới</option>';(d.networks||[]).forEach(n=>{let o=document.createElement('option');o.value=n.ssid;o.textContent=n.ssid+' | '+n.security+' | '+n.level+' dBm';s.appendChild(o)});alert(d.pending?'Scan vẫn đang chạy, bấm lại sau':'Đã nhận '+(d.networks||[]).length+' SSID')}catch(e){alert('Portal vừa khởi động lại P2P; tải lại trang rồi thử lại')}}$('ssid').onchange=()=>{$('manual').value=$('ssid').value};async function connect(){let ssid=$('manual').value||$('ssid').value;if(!ssid){alert('Nhập SSID');return}await post('/api/connect',{ssid:ssid,password:$('password').value});$('password').value='';alert('Đã gửi yêu cầu; robot sẽ rời P2P để kết nối Wi-Fi')}async function backend(){await post('/api/backend',{serial:$('serial').value,modelId:$('model').value,base:$('base').value});alert('Đã gửi yêu cầu backend')}async function stop(){await post('/api/stop',{});alert('Đã gửi yêu cầu dừng/khôi phục')}status();setInterval(status,2000);</script></body></html>";
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private void stopAndRestore() {
        setupGroupActive = false;
        stopHealthServer();
        if (p2pManager != null && p2pChannel != null) {
            p2pManager.removeGroup(p2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    appendLog("P2P group removed.");
                }

                @Override
                public void onFailure(int reason) {
                    appendLog("P2P removeGroup: " + p2pFailure(reason));
                }
            });
        }
        restorePreviousWifi();
        setupText.setText("P2P đã dừng; đang thử khôi phục Wi-Fi trước đó.");
        setStatus("Đã gửi yêu cầu Dừng/Khôi phục.");
    }

    private void stopHealthServer() {
        Thread thread = healthThread;
        healthThread = null;
        if (thread != null) {
            thread.interrupt();
        }
        ServerSocket server = healthServer;
        healthServer = null;
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void snapshotCurrentWifi() {
        if (wifiManager == null || wifiSnapshotTaken) {
            return;
        }
        try {
            WifiInfo info = wifiManager.getConnectionInfo();
            previousNetworkId = info == null ? -1 : info.getNetworkId();
            wifiSnapshotTaken = true;
            appendLog("Đã snapshot networkId hiện tại: " + previousNetworkId);
        } catch (SecurityException error) {
            appendLog("Không đọc được Wi-Fi hiện tại: permission denied.");
        }
    }

    private void restorePreviousWifi() {
        if (wifiManager == null || previousNetworkId < 0) {
            appendLog("Không có networkId cũ để khôi phục tự động.");
            return;
        }
        try {
            wifiManager.enableNetwork(previousNetworkId, true);
            wifiManager.reconnect();
            appendLog("Đã yêu cầu reconnect networkId cũ: " + previousNetworkId);
            final int networkToRemove = addedNetworkId;
            if (networkToRemove >= 0 && networkToRemove != previousNetworkId) {
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            wifiManager.removeNetwork(networkToRemove);
                        } catch (SecurityException ignored) {
                        }
                    }
                }, 8000L);
            }
        } catch (SecurityException error) {
            appendLog("Khôi phục Wi-Fi bị từ chối bởi ROM.");
        }
    }

    private void scanWifiNetworks() {
        if (!hasLocationPermission()) {
            requestWifiPermissions();
            return;
        }
        if (wifiManager == null) {
            setStatus("WifiManager không khả dụng.");
            return;
        }
        setStatus("Đang quét Wi-Fi...");
        try {
            boolean started = wifiManager.startScan();
            appendLog("startScan=" + started);
            showScanResults();
        } catch (SecurityException error) {
            setStatus("Quét Wi-Fi bị từ chối: cần quyền vị trí.");
        }
    }

    private void showScanResults() {
        recordScanResults(false);
    }

    private void showScanResultsFromBroadcast() {
        recordScanResults(true);
    }

    private void recordScanResults(boolean fromBroadcast) {
        if (wifiManager == null) {
            return;
        }
        try {
            List<ScanResult> results = wifiManager.getScanResults();
            Map<String, ScanResult> uniqueResults = new LinkedHashMap<>();
            if (results != null) {
                for (ScanResult result : results) {
                    String key = TextUtils.isEmpty(result.SSID) ? "<hidden>" : result.SSID;
                    ScanResult existing = uniqueResults.get(key);
                    if (existing == null || result.level > existing.level) {
                        uniqueResults.put(key, result);
                    }
                }
            }
            long timestamp = System.currentTimeMillis();
            String source = uniqueResults.isEmpty() ? "saved" : "scan";
            StringBuilder json = new StringBuilder("{\"ok\":true,\"scanStarted\":false,\"timestamp\":");
            json.append(timestamp).append(",\"source\":\"").append(source).append("\",\"networks\":[");
            boolean first = true;
            for (Map.Entry<String, ScanResult> entry : uniqueResults.entrySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                ScanResult result = entry.getValue();
                json.append("{\"ssid\":\"").append(jsonEscape(entry.getKey()))
                        .append("\",\"security\":\"").append(jsonEscape(security(result.capabilities)))
                        .append("\",\"level\":").append(result.level).append('}');
            }
            json.append("]}");
            synchronized (scanLock) {
                if (!uniqueResults.isEmpty()) {
                    lastScanJson = json.toString();
                }
                if (fromBroadcast && timestamp >= scanRequestAt) {
                    if (!uniqueResults.isEmpty()) {
                        lastScanAt = timestamp;
                    }
                    scanPending = false;
                    scanLock.notifyAll();
                }
            }

            StringBuilder output = new StringBuilder();
            if (uniqueResults.isEmpty()) {
                output.append("Chưa có kết quả; bấm Quét Wi-Fi lại hoặc nhập SSID thủ công.");
            } else {
                output.append("Scan timestamp: ").append(timestamp).append("\n");
                for (ScanResult result : uniqueResults.values()) {
                    String ssid = TextUtils.isEmpty(result.SSID) ? "<hidden>" : result.SSID;
                    output.append(ssid)
                            .append(" | ")
                            .append(security(result.capabilities))
                            .append(" | ")
                            .append(result.level)
                            .append(" dBm\n");
                }
                setStatus("Đã nhận " + uniqueResults.size() + " SSID Wi-Fi.");
            }
            scanText.setText(output.toString());
        } catch (SecurityException error) {
            scanText.setText("Không đọc được kết quả scan: permission denied.");
        }
    }

    private void connectToTargetWifi() {
        final String ssid = ssidInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        if (TextUtils.isEmpty(ssid)) {
            toast("Nhập SSID trước khi thử kết nối.");
            return;
        }
        connectToTargetWifi(ssid, password);
    }

    private void connectToTargetWifi(final String ssid, final String password) {
        if (!hasLocationPermission()) {
            requestWifiPermissions();
            return;
        }
        lastPortalAction = "connecting_wifi";
        snapshotCurrentWifi();
        passwordInput.setText("");
        setupGroupActive = false;
        appendLog("Switching station Wi-Fi; keeping HTTP portal alive for ADB reconfiguration.");
        if (p2pManager != null && p2pChannel != null) {
            p2pManager.removeGroup(p2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            configureStationNetwork(ssid, password);
                        }
                    }, 1000L);
                }

                @Override
                public void onFailure(int reason) {
                    configureStationNetwork(ssid, password);
                }
            });
        } else {
            configureStationNetwork(ssid, password);
        }
    }

    private void configureStationNetwork(String ssid, String password) {
        if (wifiManager == null) {
            setStatus("WifiManager không khả dụng.");
            recreateSetupGroupAfterStationAttempt();
            return;
        }
        try {
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = quote(ssid);
            if (TextUtils.isEmpty(password)) {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            } else {
                config.preSharedKey = quote(password);
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            }

            int networkId = wifiManager.addNetwork(config);
            if (networkId < 0) {
                networkId = findConfiguredNetworkId(ssid);
            } else {
                addedNetworkId = networkId;
            }
            if (networkId < 0) {
                setStatus("ROM từ chối addNetwork cho SSID đã nhập.");
                recreateSetupGroupAfterStationAttempt();
                return;
            }
            if (!wifiManager.enableNetwork(networkId, true) || !wifiManager.reconnect()) {
                setStatus("ROM từ chối enable/reconnect networkId=" + networkId);
                recreateSetupGroupAfterStationAttempt();
                return;
            }
            networkAttemptStartedAt = System.currentTimeMillis();
            setStatus("Đang kết nối Wi-Fi; timeout 60 giây...");
            pollStationConnection(networkId);
        } catch (SecurityException error) {
            setStatus("ROM chặn thao tác WifiManager: permission denied.");
            appendLog("add/enable/reconnect SecurityException");
            recreateSetupGroupAfterStationAttempt();
        }
    }

    private int findConfiguredNetworkId(String ssid) {
        try {
            List<WifiConfiguration> configured = wifiManager.getConfiguredNetworks();
            if (configured != null) {
                String quoted = quote(ssid);
                for (WifiConfiguration item : configured) {
                    if (quoted.equals(item.SSID) || ssid.equals(item.SSID)) {
                        return item.networkId;
                    }
                }
            }
        } catch (SecurityException ignored) {
        }
        return -1;
    }

    private void pollStationConnection(final int expectedNetworkId) {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    WifiInfo info = wifiManager.getConnectionInfo();
                    boolean correctNetwork = info != null && info.getNetworkId() == expectedNetworkId;
                    boolean hasIp = info != null && info.getIpAddress() != 0;
                    if (correctNetwork && hasIp) {
                        final String ip = formatIpv4(info.getIpAddress());
                        setStatus("Wi-Fi đã nhận IP: " + ip + ". Đang phát lại P2P để có thể đổi mạng tiếp.");
                        appendLog("station connected; networkId=" + expectedNetworkId + ", ip=" + ip);
                        recreateSetupGroupAfterStationAttempt();
                        return;
                    }
                } catch (SecurityException error) {
                    setStatus("Không đọc được trạng thái Wi-Fi sau khi reconnect.");
                    recreateSetupGroupAfterStationAttempt();
                    return;
                }
                if (System.currentTimeMillis() - networkAttemptStartedAt >= NETWORK_TIMEOUT_MS) {
                    setStatus("Wi-Fi timeout sau 60 giây; portal vẫn giữ để thử lại.");
                    recreateSetupGroupAfterStationAttempt();
                    return;
                }
                mainHandler.postDelayed(this, NETWORK_POLL_MS);
            }
        }, NETWORK_POLL_MS);
    }

    private void recreateSetupGroupAfterStationAttempt() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                lastPortalAction = "recreate_p2p_after_station_attempt";
                createSetupGroup();
            }
        }, 2000L);
    }

    private void checkBackendWebSocket() {
        checkBackendWebSocket(
                serialInput.getText().toString().trim(),
                modelIdInput.getText().toString().trim(),
                websocketBaseInput.getText().toString().trim());
    }

    private void checkBackendWebSocket(String serial, String modelId, String base) {
        serial = serial == null ? "" : serial.trim();
        modelId = modelId == null ? "" : modelId.trim();
        base = base == null ? "" : base.trim();
        if (TextUtils.isEmpty(serial)) {
            serial = defaultSerial();
        }
        if (TextUtils.isEmpty(modelId)) {
            modelId = BuildConfig.DEFAULT_MODEL_ID;
        }
        if (TextUtils.isEmpty(base)) {
            base = BuildConfig.DEFAULT_WEBSOCKET_BASE;
        }
        if (TextUtils.isEmpty(serial) || TextUtils.isEmpty(modelId) || TextUtils.isEmpty(base)) {
            toast("Cần serial, model ID và WebSocket base URL.");
            lastBackendResult = "missing_configuration";
            return;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        final String url = base + "/" + serial;
        lastBackendResult = "checking";
        lastPortalAction = "checking_backend";
        setStatus("Đang handshake backend, timeout 10 giây...");
        appendLog("WebSocket route: " + base + "/{serial}; header model ID đã đặt.");

        final String websocketUrl = url;
        final String modelHeader = modelId;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                runHttpsPreflight(websocketUrl);
                openBackendWebSocket(websocketUrl, modelHeader);
            }
        });
    }

    private void runHttpsPreflight(String websocketUrl) {
        String httpsUrl = websocketUrl.startsWith("wss://")
                ? "https://" + websocketUrl.substring("wss://".length())
                : websocketUrl.replaceFirst("^ws://", "http://");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(httpsUrl).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            appendLog("HTTPS preflight HTTP " + responseCode + " (network/TLS reachable; not WebSocket success).");
        } catch (UnknownHostException error) {
            appendLog("HTTPS preflight DNS failure.");
        } catch (javax.net.ssl.SSLException error) {
            appendLog("HTTPS preflight TLS failure.");
        } catch (IOException error) {
            appendLog("HTTPS preflight I/O failure: " + error.getClass().getSimpleName());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void openBackendWebSocket(final String url, String modelId) {

        if (backendClient != null) {
            backendClient.dispatcher().cancelAll();
        }
        backendClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("x-robot-model-id", modelId)
                .build();
        backendClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                lastBackendResult = "open_http_" + response.code();
                setStatus("Backend WebSocket OPEN; route/headers hợp lệ.");
                appendLog("backend onOpen HTTP " + response.code());
                webSocket.close(1000, "probe complete");
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                String code = response == null ? "no-http-response" : String.valueOf(response.code());
                lastBackendResult = "failure_http_" + code;
                setStatus("Backend handshake thất bại: HTTP " + code + ".");
                appendLog("backend failure=" + t.getClass().getSimpleName() + ", http=" + code);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                appendLog("backend closed code=" + code);
            }
        });
    }

    private String defaultSerial() {
        String serial = android.os.Build.SERIAL;
        if (serial == null || "unknown".equalsIgnoreCase(serial)) {
            return "";
        }
        return serial;
    }

    private String findP2pIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!"p2p0".equals(networkInterface.getName()) && !networkInterface.getName().startsWith("p2p")) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String findP2pInterfaceName() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.getName().startsWith("p2p")) {
                    return networkInterface.getName();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String p2pFailure(int reason) {
        switch (reason) {
            case WifiP2pManager.ERROR:
                return "ERROR";
            case WifiP2pManager.P2P_UNSUPPORTED:
                return "P2P_UNSUPPORTED";
            case WifiP2pManager.BUSY:
                return "BUSY";
            case WifiP2pManager.NO_SERVICE_REQUESTS:
                return "NO_SERVICE_REQUESTS";
            default:
                return "reason=" + reason;
        }
    }

    private String security(String capabilities) {
        if (capabilities == null || capabilities.length() == 0) {
            return "OPEN";
        }
        String upper = capabilities.toUpperCase(Locale.US);
        if (upper.contains("WEP")) {
            return "WEP";
        }
        if (upper.contains("WPA3")) {
            return "WPA3";
        }
        if (upper.contains("WPA2")) {
            return "WPA2";
        }
        if (upper.contains("WPA")) {
            return "WPA";
        }
        return capabilities;
    }

    private String quote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private String formatIpv4(int address) {
        return String.format(Locale.US, "%d.%d.%d.%d",
                address & 0xff,
                (address >> 8) & 0xff,
                (address >> 16) & 0xff,
                (address >> 24) & 0xff);
    }

    private String safe(String value) {
        return value == null || value.length() == 0 ? "<unknown>" : value;
    }

    private void setStatus(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (statusText != null) {
                    statusText.setText(message);
                }
            }
        });
    }

    private void appendLog(final String message) {
        android.util.Log.i(TAG, message);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (logText != null) {
                    String old = logText.getText().toString();
                    String next = old + "\n" + message;
                    if (next.length() > 6000) {
                        next = next.substring(next.length() - 6000);
                    }
                    logText.setText(next);
                }
            }
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        setupGroupActive = false;
        stopHealthServer();
        if (p2pManager != null && p2pChannel != null) {
            p2pManager.removeGroup(p2pChannel, null);
        }
        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        worker.shutdownNow();
        if (backendClient != null) {
            backendClient.dispatcher().cancelAll();
        }
        super.onDestroy();
    }
}
